using System.Data;
using System.Security.Cryptography;
using FamilyExpenseTracker.Api.Models;
using Npgsql;

namespace FamilyExpenseTracker.Api.Services;

public sealed class PostgresExpenseStore : IExpenseStore
{
    private static readonly TimeSpan SessionLifetime = TimeSpan.FromDays(30);
    private static readonly TimeSpan InvitationLifetime = TimeSpan.FromDays(7);

    private readonly string connectionString;

    public PostgresExpenseStore(string connectionString)
    {
        this.connectionString = connectionString;
    }

    public Result<AuthResponse> Register(RegisterRequest request)
    {
        var email = NormalizeEmail(request.Email);
        var name = request.Name.Trim();

        if (!IsValidEmail(email))
        {
            return Result<AuthResponse>.Failure(400, "invalid_email", "Email is required.");
        }

        if (request.Password.Length < 8)
        {
            return Result<AuthResponse>.Failure(400, "weak_password", "Password must contain at least 8 characters.");
        }

        if (string.IsNullOrWhiteSpace(name))
        {
            return Result<AuthResponse>.Failure(400, "invalid_name", "Name is required.");
        }

        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        if (GetUserIdByEmail(connection, transaction, email) is not null)
        {
            return Result<AuthResponse>.Failure(400, "email_exists", "User with this email already exists.");
        }

        var (hash, salt) = PasswordHasher.Hash(request.Password);
        var userId = Guid.NewGuid();
        var now = DateTimeOffset.UtcNow;

        using (var command = connection.CreateCommand())
        {
            command.Transaction = transaction;
            command.CommandText = """
                INSERT INTO users(id, email, name, password_hash, password_salt, created_at)
                VALUES (@id, @email, @name, @password_hash, @password_salt, @created_at);
                """;
            Add(command, "id", userId);
            Add(command, "email", email);
            Add(command, "name", name);
            Add(command, "password_hash", hash);
            Add(command, "password_salt", salt);
            Add(command, "created_at", now);
            command.ExecuteNonQuery();
        }

        var auth = CreateSession(connection, transaction, userId, email, name, now);
        transaction.Commit();

        return Result<AuthResponse>.Success(auth);
    }

    public Result<AuthResponse> Login(LoginRequest request)
    {
        var email = NormalizeEmail(request.Email);

        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT id, email, name, password_hash, password_salt
            FROM users
            WHERE lower(email) = lower(@email)
            LIMIT 1;
            """;
        Add(command, "email", email);

        using var reader = command.ExecuteReader();
        if (!reader.Read())
        {
            return Result<AuthResponse>.Failure(400, "invalid_credentials", "Email or password is incorrect.");
        }

        var userId = reader.GetGuid(0);
        var storedEmail = reader.GetString(1);
        var name = reader.GetString(2);
        var hash = reader.GetString(3);
        var salt = reader.GetString(4);
        reader.Close();

        if (!PasswordHasher.Verify(request.Password, hash, salt))
        {
            return Result<AuthResponse>.Failure(400, "invalid_credentials", "Email or password is incorrect.");
        }

        using var transaction = connection.BeginTransaction();
        var auth = CreateSession(connection, transaction, userId, storedEmail, name, DateTimeOffset.UtcNow);
        transaction.Commit();

        return Result<AuthResponse>.Success(auth);
    }

    public bool TryGetUserIdForToken(string token, out Guid userId)
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT user_id
            FROM sessions
            WHERE token = @token
              AND expires_at > now()
            LIMIT 1;
            """;
        Add(command, "token", token);

        var value = command.ExecuteScalar();
        if (value is Guid id)
        {
            userId = id;
            return true;
        }

        userId = Guid.Empty;
        return false;
    }

    public IReadOnlyList<HouseholdResponse> GetHouseholds(Guid userId)
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT h.id, h.name, h.base_currency, hm.role, h.created_at
            FROM households h
            JOIN household_members hm ON hm.household_id = h.id
            WHERE hm.user_id = @user_id
            ORDER BY h.name;
            """;
        Add(command, "user_id", userId);

        using var reader = command.ExecuteReader();
        var items = new List<HouseholdResponse>();
        while (reader.Read())
        {
            items.Add(ReadHousehold(reader));
        }

        return items;
    }

    public Result<HouseholdResponse> CreateHousehold(Guid userId, CreateHouseholdRequest request)
    {
        var name = request.Name.Trim();
        var currency = NormalizeCurrency(request.BaseCurrency);

        if (string.IsNullOrWhiteSpace(name))
        {
            return Result<HouseholdResponse>.Failure(400, "invalid_household_name", "Household name is required.");
        }

        if (currency.Length != 3)
        {
            return Result<HouseholdResponse>.Failure(400, "invalid_currency", "Base currency must be a 3-letter ISO code.");
        }

        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id, name, base_currency, role, created_at FROM api_create_household(@user_id, @name, @base_currency);";
        Add(command, "user_id", userId);
        Add(command, "name", name);
        Add(command, "base_currency", currency);

        using var reader = command.ExecuteReader();
        return reader.Read()
            ? Result<HouseholdResponse>.Success(ReadHousehold(reader))
            : Result<HouseholdResponse>.Failure(400, "household_create_failed", "Household was not created.");
    }

    public Result<InvitationResponse> CreateInvitation(Guid userId, Guid householdId, CreateInvitationRequest request)
    {
        var email = NormalizeEmail(request.Email);
        if (!IsValidEmail(email))
        {
            return Result<InvitationResponse>.Failure(400, "invalid_email", "Invitee email is required.");
        }

        using var connection = OpenConnection();
        if (!IsOwner(connection, userId, householdId))
        {
            return IsHouseholdMember(connection, userId, householdId)
                ? Result<InvitationResponse>.Failure(400, "not_household_owner", "Only household owners can invite users.")
                : Result<InvitationResponse>.Failure(404, "household_not_found", "Household was not found.");
        }

        var token = CreateOpaqueToken();
        var expiresAt = DateTimeOffset.UtcNow.Add(InvitationLifetime);

        using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO invitations(token, household_id, email, expires_at, created_by)
            VALUES (@token, @household_id, @email, @expires_at, @created_by);
            """;
        Add(command, "token", token);
        Add(command, "household_id", householdId);
        Add(command, "email", email);
        Add(command, "expires_at", expiresAt);
        Add(command, "created_by", userId);
        command.ExecuteNonQuery();

        return Result<InvitationResponse>.Success(new InvitationResponse(householdId, email, token, expiresAt));
    }

    public Result<HouseholdResponse> AcceptInvitation(Guid userId, string token)
    {
        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        using var invitationCommand = connection.CreateCommand();
        invitationCommand.Transaction = transaction;
        invitationCommand.CommandText = """
            SELECT i.household_id, h.name, h.base_currency, i.expires_at
            FROM invitations i
            JOIN households h ON h.id = i.household_id
            WHERE i.token = @token
            LIMIT 1;
            """;
        Add(invitationCommand, "token", token);

        using var reader = invitationCommand.ExecuteReader();
        if (!reader.Read() || reader.GetFieldValue<DateTimeOffset>(3) <= DateTimeOffset.UtcNow)
        {
            return Result<HouseholdResponse>.Failure(404, "invitation_not_found", "Invitation was not found or has expired.");
        }

        var householdId = reader.GetGuid(0);
        var householdName = reader.GetString(1);
        var baseCurrency = reader.GetString(2).Trim();
        reader.Close();

        using (var memberCommand = connection.CreateCommand())
        {
            memberCommand.Transaction = transaction;
            memberCommand.CommandText = """
                INSERT INTO household_members(household_id, user_id, role)
                VALUES (@household_id, @user_id, 'Member')
                ON CONFLICT (household_id, user_id) DO NOTHING;
                """;
            Add(memberCommand, "household_id", householdId);
            Add(memberCommand, "user_id", userId);
            memberCommand.ExecuteNonQuery();
        }

        using (var deleteCommand = connection.CreateCommand())
        {
            deleteCommand.Transaction = transaction;
            deleteCommand.CommandText = "DELETE FROM invitations WHERE token = @token;";
            Add(deleteCommand, "token", token);
            deleteCommand.ExecuteNonQuery();
        }

        transaction.Commit();

        return Result<HouseholdResponse>.Success(new HouseholdResponse(householdId, householdName, baseCurrency, "Member", DateTimeOffset.UtcNow));
    }

    public Result<IReadOnlyList<AccountResponse>> GetAccounts(Guid userId, Guid householdId)
    {
        using var connection = OpenConnection();
        if (!IsHouseholdMember(connection, userId, householdId))
        {
            return Result<IReadOnlyList<AccountResponse>>.Failure(404, "household_not_found", "Household was not found.");
        }

        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT id, household_id, name, currency, type, bank_name, account_hint, created_at
            FROM accounts
            WHERE household_id = @household_id
            ORDER BY name;
            """;
        Add(command, "household_id", householdId);

        using var reader = command.ExecuteReader();
        var accounts = new List<AccountResponse>();
        while (reader.Read())
        {
            accounts.Add(ReadAccount(reader));
        }

        return Result<IReadOnlyList<AccountResponse>>.Success(accounts);
    }

    public Result<AccountResponse> CreateAccount(Guid userId, Guid householdId, CreateAccountRequest request)
    {
        var name = request.Name.Trim();
        var currency = NormalizeCurrency(request.Currency);
        var type = request.Type.Trim();

        if (string.IsNullOrWhiteSpace(name))
        {
            return Result<AccountResponse>.Failure(400, "invalid_account_name", "Account name is required.");
        }

        if (currency.Length != 3)
        {
            return Result<AccountResponse>.Failure(400, "invalid_currency", "Currency must be a 3-letter ISO code.");
        }

        if (string.IsNullOrWhiteSpace(type))
        {
            return Result<AccountResponse>.Failure(400, "invalid_account_type", "Account type is required.");
        }

        using var connection = OpenConnection();
        if (!IsHouseholdMember(connection, userId, householdId))
        {
            return Result<AccountResponse>.Failure(404, "household_not_found", "Household was not found.");
        }

        using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO accounts(household_id, name, currency, type, bank_name, account_hint)
            VALUES (@household_id, @name, @currency, @type, @bank_name, @account_hint)
            RETURNING id, household_id, name, currency, type, bank_name, account_hint, created_at;
            """;
        Add(command, "household_id", householdId);
        Add(command, "name", name);
        Add(command, "currency", currency);
        Add(command, "type", type);
        AddNullable(command, "bank_name", BlankToNull(request.BankName));
        AddNullable(command, "account_hint", BlankToNull(request.AccountHint));

        using var reader = command.ExecuteReader();
        return reader.Read()
            ? Result<AccountResponse>.Success(ReadAccount(reader))
            : Result<AccountResponse>.Failure(400, "account_create_failed", "Account was not created.");
    }

    public Result<TransactionSyncResponse> GetTransactions(Guid userId, Guid householdId, long? sinceVersion)
    {
        using var connection = OpenConnection();
        try
        {
            using var command = connection.CreateCommand();
            command.CommandText = "SELECT * FROM api_get_transactions_since(@user_id, @household_id, @since_version);";
            Add(command, "user_id", userId);
            Add(command, "household_id", householdId);
            Add(command, "since_version", sinceVersion.GetValueOrDefault(0));

            using var reader = command.ExecuteReader();
            var transactions = new List<TransactionResponse>();
            while (reader.Read())
            {
                transactions.Add(ReadTransaction(reader));
            }
            reader.Close();

            var cursor = GetHouseholdCursorVersion(connection, householdId);
            return Result<TransactionSyncResponse>.Success(new TransactionSyncResponse(householdId, cursor, transactions));
        }
        catch (PostgresException ex) when (IsRaised(ex, "household_not_found"))
        {
            return Result<TransactionSyncResponse>.Failure(404, "household_not_found", "Household was not found.");
        }
    }

    public Result<TransactionResponse> CreateTransaction(Guid userId, Guid householdId, CreateTransactionRequest request)
    {
        if (request.Amount <= 0)
        {
            return Result<TransactionResponse>.Failure(400, "invalid_amount", "Amount must be greater than zero.");
        }

        var currency = NormalizeCurrency(request.Currency);
        if (currency.Length != 3)
        {
            return Result<TransactionResponse>.Failure(400, "invalid_currency", "Currency must be a 3-letter ISO code.");
        }

        if (string.IsNullOrWhiteSpace(request.TransactionName))
        {
            return Result<TransactionResponse>.Failure(400, "invalid_transaction_name", "Transaction name is required.");
        }

        using var connection = OpenConnection();
        try
        {
            using var command = connection.CreateCommand();
            command.CommandText = """
                SELECT * FROM api_create_transaction(
                    @user_id,
                    @household_id,
                    @client_id,
                    @account_id,
                    @amount,
                    @currency,
                    @transaction_name,
                    @merchant,
                    @category_id,
                    @source,
                    @operation_type,
                    @occurred_at,
                    @client_created_at,
                    @available_balance,
                    @external_fingerprint
                );
                """;
            Add(command, "user_id", userId);
            Add(command, "household_id", householdId);
            AddNullable(command, "client_id", request.ClientId);
            Add(command, "account_id", request.AccountId);
            Add(command, "amount", request.Amount);
            Add(command, "currency", currency);
            Add(command, "transaction_name", request.TransactionName.Trim());
            AddNullable(command, "merchant", BlankToNull(request.Merchant));
            AddNullable(command, "category_id", request.CategoryId);
            Add(command, "source", request.Source.Trim());
            Add(command, "operation_type", request.OperationType.Trim());
            Add(command, "occurred_at", request.OccurredAt);
            AddNullable(command, "client_created_at", request.ClientCreatedAt);
            AddNullable(command, "available_balance", request.AvailableBalance);
            AddNullable(command, "external_fingerprint", BlankToNull(request.ExternalFingerprint));

            using var reader = command.ExecuteReader();
            return reader.Read()
                ? Result<TransactionResponse>.Success(ReadTransaction(reader))
                : Result<TransactionResponse>.Failure(400, "transaction_create_failed", "Transaction was not created.");
        }
        catch (PostgresException ex) when (IsRaised(ex, "household_not_found"))
        {
            return Result<TransactionResponse>.Failure(404, "household_not_found", "Household was not found.");
        }
        catch (PostgresException ex) when (IsRaised(ex, "account_not_found"))
        {
            return Result<TransactionResponse>.Failure(404, "account_not_found", "Account was not found.");
        }
    }

    public Result<TransactionResponse> UpdateTransaction(Guid userId, Guid householdId, Guid transactionId, UpdateTransactionRequest request) =>
        Result<TransactionResponse>.Failure(400, "postgres_update_not_implemented", "PostgreSQL transaction update will be added in the next backend slice.");

    public Result<TransactionResponse> DeleteTransaction(Guid userId, Guid householdId, Guid transactionId, DeleteTransactionRequest request)
    {
        using var connection = OpenConnection();
        try
        {
            using var command = connection.CreateCommand();
            command.CommandText = "SELECT * FROM api_delete_transaction(@user_id, @household_id, @transaction_id, @base_version);";
            Add(command, "user_id", userId);
            Add(command, "household_id", householdId);
            Add(command, "transaction_id", transactionId);
            Add(command, "base_version", request.BaseVersion);

            using var reader = command.ExecuteReader();
            return reader.Read()
                ? Result<TransactionResponse>.Success(ReadTransaction(reader))
                : Result<TransactionResponse>.Failure(400, "transaction_delete_failed", "Transaction was not deleted.");
        }
        catch (PostgresException ex) when (IsRaised(ex, "household_not_found"))
        {
            return Result<TransactionResponse>.Failure(404, "household_not_found", "Household was not found.");
        }
        catch (PostgresException ex) when (IsRaised(ex, "transaction_not_found"))
        {
            return Result<TransactionResponse>.Failure(404, "transaction_not_found", "Transaction was not found.");
        }
        catch (PostgresException ex) when (ex.MessageText.StartsWith("transaction_conflict", StringComparison.Ordinal))
        {
            return Result<TransactionResponse>.Failure(409, "transaction_conflict", "Transaction was changed on the server.", ["DeletedAt"]);
        }
    }

    private AuthResponse CreateSession(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid userId,
        string email,
        string name,
        DateTimeOffset now)
    {
        var token = CreateOpaqueToken();
        var expiresAt = now.Add(SessionLifetime);

        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO sessions(token, user_id, expires_at, created_at)
            VALUES (@token, @user_id, @expires_at, @created_at);
            """;
        Add(command, "token", token);
        Add(command, "user_id", userId);
        Add(command, "expires_at", expiresAt);
        Add(command, "created_at", now);
        command.ExecuteNonQuery();

        return new AuthResponse(userId, email, name, token, expiresAt);
    }

    private Guid? GetUserIdByEmail(NpgsqlConnection connection, NpgsqlTransaction transaction, string email)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "SELECT id FROM users WHERE lower(email) = lower(@email) LIMIT 1;";
        Add(command, "email", email);
        return command.ExecuteScalar() as Guid?;
    }

    private bool IsHouseholdMember(NpgsqlConnection connection, Guid userId, Guid householdId)
    {
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT EXISTS (
                SELECT 1
                FROM household_members
                WHERE user_id = @user_id
                  AND household_id = @household_id
            );
            """;
        Add(command, "user_id", userId);
        Add(command, "household_id", householdId);
        return (bool)command.ExecuteScalar()!;
    }

    private bool IsOwner(NpgsqlConnection connection, Guid userId, Guid householdId)
    {
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT EXISTS (
                SELECT 1
                FROM household_members
                WHERE user_id = @user_id
                  AND household_id = @household_id
                  AND role = 'Owner'
            );
            """;
        Add(command, "user_id", userId);
        Add(command, "household_id", householdId);
        return (bool)command.ExecuteScalar()!;
    }

    private long GetHouseholdCursorVersion(NpgsqlConnection connection, Guid householdId)
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COALESCE(MAX(version), 0) FROM transactions WHERE household_id = @household_id;";
        Add(command, "household_id", householdId);
        return Convert.ToInt64(command.ExecuteScalar());
    }

    private NpgsqlConnection OpenConnection()
    {
        var connection = new NpgsqlConnection(connectionString);
        connection.Open();
        return connection;
    }

    private static HouseholdResponse ReadHousehold(IDataRecord reader) =>
        new(
            reader.GetGuid(0),
            reader.GetString(1),
            reader.GetString(2).Trim(),
            reader.GetString(3),
            GetDateTimeOffset(reader, 4));

    private static AccountResponse ReadAccount(IDataRecord reader) =>
        new(
            reader.GetGuid(0),
            reader.GetGuid(1),
            reader.GetString(2),
            reader.GetString(3).Trim(),
            reader.GetString(4),
            reader.IsDBNull(5) ? null : reader.GetString(5),
            reader.IsDBNull(6) ? null : reader.GetString(6),
            GetDateTimeOffset(reader, 7));

    private static TransactionResponse ReadTransaction(IDataRecord reader) =>
        new(
            reader.GetGuid(reader.GetOrdinal("id")),
            reader.GetGuid(reader.GetOrdinal("household_id")),
            reader.GetGuid(reader.GetOrdinal("account_id")),
            reader.GetGuid(reader.GetOrdinal("created_by")),
            reader.GetDecimal(reader.GetOrdinal("amount")),
            reader.GetString(reader.GetOrdinal("currency")).Trim(),
            reader.GetString(reader.GetOrdinal("transaction_name")),
            GetNullableString(reader, "merchant"),
            GetNullableGuid(reader, "category_id"),
            reader.GetString(reader.GetOrdinal("source")),
            reader.GetString(reader.GetOrdinal("operation_type")),
            GetDateTimeOffset(reader, reader.GetOrdinal("occurred_at")),
            GetDateTimeOffset(reader, reader.GetOrdinal("received_at")),
            GetNullableDateTimeOffset(reader, "client_created_at"),
            GetNullableDecimal(reader, "available_balance"),
            reader.GetInt64(reader.GetOrdinal("version")),
            GetDateTimeOffset(reader, reader.GetOrdinal("updated_at")),
            GetNullableDateTimeOffset(reader, "deleted_at"),
            GetNullableGuid(reader, "merged_into_transaction_id"),
            GetNullableString(reader, "external_fingerprint"));

    private static string? GetNullableString(IDataRecord reader, string name)
    {
        var ordinal = reader.GetOrdinal(name);
        return reader.IsDBNull(ordinal) ? null : reader.GetString(ordinal);
    }

    private static Guid? GetNullableGuid(IDataRecord reader, string name)
    {
        var ordinal = reader.GetOrdinal(name);
        return reader.IsDBNull(ordinal) ? null : reader.GetGuid(ordinal);
    }

    private static decimal? GetNullableDecimal(IDataRecord reader, string name)
    {
        var ordinal = reader.GetOrdinal(name);
        return reader.IsDBNull(ordinal) ? null : reader.GetDecimal(ordinal);
    }

    private static DateTimeOffset? GetNullableDateTimeOffset(IDataRecord reader, string name)
    {
        var ordinal = reader.GetOrdinal(name);
        return reader.IsDBNull(ordinal) ? null : GetDateTimeOffset(reader, ordinal);
    }

    private static DateTimeOffset GetDateTimeOffset(IDataRecord reader, int ordinal)
    {
        var value = reader.GetValue(ordinal);
        return value switch
        {
            DateTimeOffset offset => offset,
            DateTime dateTime => new DateTimeOffset(DateTime.SpecifyKind(dateTime, DateTimeKind.Utc)),
            _ => throw new InvalidCastException($"Cannot convert {value.GetType().FullName} to DateTimeOffset."),
        };
    }

    private static bool IsRaised(PostgresException exception, string message) =>
        exception.SqlState == "P0001" && string.Equals(exception.MessageText, message, StringComparison.Ordinal);

    private static void Add(NpgsqlCommand command, string name, object value) =>
        command.Parameters.AddWithValue(name, value);

    private static void AddNullable(NpgsqlCommand command, string name, object? value) =>
        command.Parameters.AddWithValue(name, value ?? DBNull.Value);

    private static string CreateOpaqueToken() =>
        Convert.ToBase64String(RandomNumberGenerator.GetBytes(32))
            .Replace("+", "-", StringComparison.Ordinal)
            .Replace("/", "_", StringComparison.Ordinal)
            .TrimEnd('=');

    private static string NormalizeEmail(string value) => value.Trim().ToLowerInvariant();

    private static string NormalizeCurrency(string value) => value.Trim().ToUpperInvariant();

    private static bool IsValidEmail(string value) => value.Contains('@', StringComparison.Ordinal) && value.Contains('.', StringComparison.Ordinal);

    private static string? BlankToNull(string? value) =>
        string.IsNullOrWhiteSpace(value) ? null : value.Trim();
}
