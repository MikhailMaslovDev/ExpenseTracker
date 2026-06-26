using System;
using System.Collections.Generic;
using System.Linq;
using System.Security.Cryptography;
using FamilyExpenseTracker.Api.Models;

namespace FamilyExpenseTracker.Api.Services;

public sealed class ExpenseStore : IExpenseStore
{
    private static readonly TimeSpan SessionLifetime = TimeSpan.FromDays(30);
    private static readonly TimeSpan InvitationLifetime = TimeSpan.FromDays(7);

    private readonly object gate = new();
    private readonly Dictionary<Guid, UserRecord> users = new();
    private readonly Dictionary<string, Guid> userIdByEmail = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, SessionRecord> sessions = new(StringComparer.Ordinal);
    private readonly Dictionary<Guid, HouseholdRecord> households = new();
    private readonly List<HouseholdMemberRecord> members = new();
    private readonly Dictionary<string, InvitationRecord> invitations = new(StringComparer.Ordinal);
    private readonly Dictionary<Guid, AccountRecord> accounts = new();
    private readonly Dictionary<Guid, TransactionRecord> transactions = new();
    private readonly List<TransactionAuditRecord> auditLog = new();
    private readonly List<CategoryRecord> categories = new();
    private readonly List<CategoryRuleRecord> categoryRules = new();
    private readonly List<BankNotificationTemplateRecord> bankTemplates = new();
    private readonly List<AiCategorizationResultRecord> aiResults = new();

    public ExpenseStore()
    {
        SeedSystemCategories();
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

        lock (gate)
        {
            if (userIdByEmail.ContainsKey(email))
            {
                return Result<AuthResponse>.Failure(400, "email_exists", "User with this email already exists.");
            }

            var now = DateTimeOffset.UtcNow;
            var (hash, salt) = PasswordHasher.Hash(request.Password);
            var user = new UserRecord
            {
                Id = Guid.NewGuid(),
                Email = email,
                Name = name,
                PasswordHash = hash,
                PasswordSalt = salt,
                CreatedAt = now,
            };

            users[user.Id] = user;
            userIdByEmail[email] = user.Id;

            return Result<AuthResponse>.Success(CreateSession(user, now));
        }
    }

    public Result<AuthResponse> Login(LoginRequest request)
    {
        var email = NormalizeEmail(request.Email);

        lock (gate)
        {
            if (!userIdByEmail.TryGetValue(email, out var userId) ||
                !users.TryGetValue(userId, out var user) ||
                !PasswordHasher.Verify(request.Password, user.PasswordHash, user.PasswordSalt))
            {
                return Result<AuthResponse>.Failure(400, "invalid_credentials", "Email or password is incorrect.");
            }

            return Result<AuthResponse>.Success(CreateSession(user, DateTimeOffset.UtcNow));
        }
    }

    public bool TryGetUserIdForToken(string token, out Guid userId)
    {
        lock (gate)
        {
            if (sessions.TryGetValue(token, out var session) && session.ExpiresAt > DateTimeOffset.UtcNow)
            {
                userId = session.UserId;
                return true;
            }
        }

        userId = Guid.Empty;
        return false;
    }

    public IReadOnlyList<HouseholdResponse> GetHouseholds(Guid userId)
    {
        lock (gate)
        {
            return members
                .Where(member => member.UserId == userId)
                .Select(member => ToHouseholdResponse(households[member.HouseholdId], member.Role))
                .OrderBy(response => response.Name)
                .ToList();
        }
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

        lock (gate)
        {
            if (!users.ContainsKey(userId))
            {
                return Result<HouseholdResponse>.Failure(404, "user_not_found", "User was not found.");
            }

            var now = DateTimeOffset.UtcNow;
            var household = new HouseholdRecord
            {
                Id = Guid.NewGuid(),
                Name = name,
                BaseCurrency = currency,
                CreatedBy = userId,
                CreatedAt = now,
            };

            households[household.Id] = household;
            members.Add(new HouseholdMemberRecord
            {
                HouseholdId = household.Id,
                UserId = userId,
                Role = "Owner",
                JoinedAt = now,
            });

            var defaultAccount = new AccountRecord
            {
                Id = Guid.NewGuid(),
                HouseholdId = household.Id,
                Name = "Main account",
                Currency = currency,
                Type = "Cash",
                CreatedAt = now,
            };
            accounts[defaultAccount.Id] = defaultAccount;

            return Result<HouseholdResponse>.Success(ToHouseholdResponse(household, "Owner"));
        }
    }

    public Result<InvitationResponse> CreateInvitation(Guid userId, Guid householdId, CreateInvitationRequest request)
    {
        var email = NormalizeEmail(request.Email);

        if (!IsValidEmail(email))
        {
            return Result<InvitationResponse>.Failure(400, "invalid_email", "Invitee email is required.");
        }

        lock (gate)
        {
            var membership = FindMembership(userId, householdId);
            if (membership is null)
            {
                return Result<InvitationResponse>.Failure(404, "household_not_found", "Household was not found.");
            }

            if (!string.Equals(membership.Role, "Owner", StringComparison.OrdinalIgnoreCase))
            {
                return Result<InvitationResponse>.Failure(400, "not_household_owner", "Only household owners can invite users.");
            }

            var token = CreateOpaqueToken();
            var invitation = new InvitationRecord
            {
                HouseholdId = householdId,
                Email = email,
                Token = token,
                ExpiresAt = DateTimeOffset.UtcNow.Add(InvitationLifetime),
                CreatedBy = userId,
            };
            invitations[token] = invitation;

            return Result<InvitationResponse>.Success(new InvitationResponse(householdId, email, token, invitation.ExpiresAt));
        }
    }

    public Result<HouseholdResponse> AcceptInvitation(Guid userId, string token)
    {
        lock (gate)
        {
            if (!invitations.TryGetValue(token, out var invitation) || invitation.ExpiresAt <= DateTimeOffset.UtcNow)
            {
                return Result<HouseholdResponse>.Failure(404, "invitation_not_found", "Invitation was not found or has expired.");
            }

            if (!households.TryGetValue(invitation.HouseholdId, out var household))
            {
                return Result<HouseholdResponse>.Failure(404, "household_not_found", "Household was not found.");
            }

            if (FindMembership(userId, invitation.HouseholdId) is null)
            {
                members.Add(new HouseholdMemberRecord
                {
                    HouseholdId = invitation.HouseholdId,
                    UserId = userId,
                    Role = "Member",
                    JoinedAt = DateTimeOffset.UtcNow,
                });
            }

            invitations.Remove(token);
            return Result<HouseholdResponse>.Success(ToHouseholdResponse(household, "Member"));
        }
    }

    public Result<IReadOnlyList<AccountResponse>> GetAccounts(Guid userId, Guid householdId)
    {
        lock (gate)
        {
            if (FindMembership(userId, householdId) is null)
            {
                return Result<IReadOnlyList<AccountResponse>>.Failure(404, "household_not_found", "Household was not found.");
            }

            return Result<IReadOnlyList<AccountResponse>>.Success(accounts.Values
                .Where(account => account.HouseholdId == householdId)
                .Select(ToAccountResponse)
                .OrderBy(account => account.Name)
                .ToList());
        }
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

        lock (gate)
        {
            if (FindMembership(userId, householdId) is null)
            {
                return Result<AccountResponse>.Failure(404, "household_not_found", "Household was not found.");
            }

            var account = new AccountRecord
            {
                Id = Guid.NewGuid(),
                HouseholdId = householdId,
                Name = name,
                Currency = currency,
                Type = type,
                BankName = BlankToNull(request.BankName),
                AccountHint = BlankToNull(request.AccountHint),
                CreatedAt = DateTimeOffset.UtcNow,
            };
            accounts[account.Id] = account;

            return Result<AccountResponse>.Success(ToAccountResponse(account));
        }
    }

    public Result<TransactionSyncResponse> GetTransactions(Guid userId, Guid householdId, long? sinceVersion)
    {
        lock (gate)
        {
            if (FindMembership(userId, householdId) is null)
            {
                return Result<TransactionSyncResponse>.Failure(404, "household_not_found", "Household was not found.");
            }

            var cursor = sinceVersion.GetValueOrDefault(0);
            var items = transactions.Values
                .Where(transaction => transaction.HouseholdId == householdId && transaction.Version > cursor)
                .OrderByDescending(transaction => transaction.UpdatedAt)
                .Select(ToTransactionResponse)
                .ToList();

            var maxVersion = transactions.Values
                .Where(transaction => transaction.HouseholdId == householdId)
                .Select(transaction => transaction.Version)
                .DefaultIfEmpty(0)
                .Max();

            return Result<TransactionSyncResponse>.Success(new TransactionSyncResponse(householdId, maxVersion, items));
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

        var transactionName = request.TransactionName.Trim();
        if (string.IsNullOrWhiteSpace(transactionName))
        {
            return Result<TransactionResponse>.Failure(400, "invalid_transaction_name", "Transaction name is required.");
        }

        lock (gate)
        {
            if (FindMembership(userId, householdId) is null)
            {
                return Result<TransactionResponse>.Failure(404, "household_not_found", "Household was not found.");
            }

            if (!accounts.TryGetValue(request.AccountId, out var account) || account.HouseholdId != householdId)
            {
                return Result<TransactionResponse>.Failure(404, "account_not_found", "Account was not found.");
            }

            var duplicate = FindDuplicateByFingerprint(householdId, request.ExternalFingerprint);
            if (duplicate is not null)
            {
                return Result<TransactionResponse>.Success(ToTransactionResponse(duplicate));
            }

            var now = DateTimeOffset.UtcNow;
            var transaction = new TransactionRecord
            {
                Id = request.ClientId.GetValueOrDefault(Guid.NewGuid()),
                HouseholdId = householdId,
                AccountId = request.AccountId,
                CreatedBy = userId,
                Amount = request.Amount,
                Currency = currency,
                TransactionName = transactionName,
                Merchant = BlankToNull(request.Merchant),
                CategoryId = request.CategoryId,
                Source = request.Source.Trim(),
                OperationType = request.OperationType.Trim(),
                OccurredAt = request.OccurredAt,
                ReceivedAt = now,
                ClientCreatedAt = request.ClientCreatedAt,
                AvailableBalance = request.AvailableBalance,
                Version = NextVersion(householdId),
                UpdatedAt = now,
                ExternalFingerprint = BlankToNull(request.ExternalFingerprint),
                FieldVersions = new Dictionary<string, long>(StringComparer.Ordinal)
            };

            MarkAllFieldsChanged(transaction, transaction.Version);
            transactions[transaction.Id] = transaction;
            WriteAudit(householdId, transaction.Id, userId, "Created", transaction.Version, transaction.FieldVersions.Keys.ToList(), now);

            return Result<TransactionResponse>.Success(ToTransactionResponse(transaction));
        }
    }

    public Result<TransactionResponse> UpdateTransaction(Guid userId, Guid householdId, Guid transactionId, UpdateTransactionRequest request)
    {
        lock (gate)
        {
            if (FindMembership(userId, householdId) is null)
            {
                return Result<TransactionResponse>.Failure(404, "household_not_found", "Household was not found.");
            }

            if (!transactions.TryGetValue(transactionId, out var transaction) || transaction.HouseholdId != householdId)
            {
                return Result<TransactionResponse>.Failure(404, "transaction_not_found", "Transaction was not found.");
            }

            var patch = BuildPatch(request);
            if (patch.Count == 0)
            {
                return Result<TransactionResponse>.Success(ToTransactionResponse(transaction));
            }

            var conflicts = patch.Keys
                .Where(field => transaction.FieldVersions.TryGetValue(field, out var fieldVersion) && fieldVersion > request.BaseVersion)
                .ToList();

            if (conflicts.Count > 0)
            {
                return Result<TransactionResponse>.Failure(
                    409,
                    "transaction_conflict",
                    "Transaction was changed on the server. Resolve conflicting fields and retry.",
                    conflicts);
            }

            var now = DateTimeOffset.UtcNow;
            var nextVersion = NextVersion(householdId);

            ApplyPatch(transaction, patch);
            transaction.Version = nextVersion;
            transaction.UpdatedAt = now;
            foreach (var field in patch.Keys)
            {
                transaction.FieldVersions[field] = nextVersion;
            }

            WriteAudit(householdId, transactionId, userId, "Updated", nextVersion, patch.Keys.ToList(), now);
            return Result<TransactionResponse>.Success(ToTransactionResponse(transaction));
        }
    }

    public Result<TransactionResponse> DeleteTransaction(Guid userId, Guid householdId, Guid transactionId, DeleteTransactionRequest request)
    {
        lock (gate)
        {
            if (FindMembership(userId, householdId) is null)
            {
                return Result<TransactionResponse>.Failure(404, "household_not_found", "Household was not found.");
            }

            if (!transactions.TryGetValue(transactionId, out var transaction) || transaction.HouseholdId != householdId)
            {
                return Result<TransactionResponse>.Failure(404, "transaction_not_found", "Transaction was not found.");
            }

            if (transaction.FieldVersions.TryGetValue(nameof(TransactionRecord.DeletedAt), out var deletedVersion) &&
                deletedVersion > request.BaseVersion)
            {
                return Result<TransactionResponse>.Failure(
                    409,
                    "transaction_conflict",
                    "Transaction delete state was changed on the server.",
                    [nameof(TransactionRecord.DeletedAt)]);
            }

            var now = DateTimeOffset.UtcNow;
            var nextVersion = NextVersion(householdId);
            transaction.DeletedAt = now;
            transaction.Version = nextVersion;
            transaction.UpdatedAt = now;
            transaction.FieldVersions[nameof(TransactionRecord.DeletedAt)] = nextVersion;

            WriteAudit(householdId, transactionId, userId, "Deleted", nextVersion, [nameof(TransactionRecord.DeletedAt)], now);
            return Result<TransactionResponse>.Success(ToTransactionResponse(transaction));
        }
    }

    private AuthResponse CreateSession(UserRecord user, DateTimeOffset now)
    {
        var token = CreateOpaqueToken();
        var session = new SessionRecord
        {
            Token = token,
            UserId = user.Id,
            ExpiresAt = now.Add(SessionLifetime),
        };
        sessions[token] = session;

        return new AuthResponse(user.Id, user.Email, user.Name, token, session.ExpiresAt);
    }

    private HouseholdMemberRecord? FindMembership(Guid userId, Guid householdId) =>
        members.FirstOrDefault(member => member.UserId == userId && member.HouseholdId == householdId);

    private TransactionRecord? FindDuplicateByFingerprint(Guid householdId, string? externalFingerprint)
    {
        var fingerprint = BlankToNull(externalFingerprint);
        return fingerprint is null
            ? null
            : transactions.Values.FirstOrDefault(transaction =>
                transaction.HouseholdId == householdId &&
                string.Equals(transaction.ExternalFingerprint, fingerprint, StringComparison.Ordinal));
    }

    private long NextVersion(Guid householdId) =>
        transactions.Values
            .Where(transaction => transaction.HouseholdId == householdId)
            .Select(transaction => transaction.Version)
            .DefaultIfEmpty(0)
            .Max() + 1;

    private static Dictionary<string, object?> BuildPatch(UpdateTransactionRequest request)
    {
        var patch = new Dictionary<string, object?>(StringComparer.Ordinal);

        if (request.Amount is not null)
        {
            patch[nameof(TransactionRecord.Amount)] = request.Amount.Value;
        }

        if (request.Currency is not null)
        {
            patch[nameof(TransactionRecord.Currency)] = NormalizeCurrency(request.Currency);
        }

        if (request.TransactionName is not null)
        {
            patch[nameof(TransactionRecord.TransactionName)] = request.TransactionName.Trim();
        }

        if (request.Merchant is not null)
        {
            patch[nameof(TransactionRecord.Merchant)] = BlankToNull(request.Merchant);
        }

        if (request.CategoryId is not null)
        {
            patch[nameof(TransactionRecord.CategoryId)] = request.CategoryId;
        }

        if (request.OperationType is not null)
        {
            patch[nameof(TransactionRecord.OperationType)] = request.OperationType.Trim();
        }

        if (request.OccurredAt is not null)
        {
            patch[nameof(TransactionRecord.OccurredAt)] = request.OccurredAt.Value;
        }

        if (request.AvailableBalance is not null)
        {
            patch[nameof(TransactionRecord.AvailableBalance)] = request.AvailableBalance.Value;
        }

        return patch;
    }

    private static void ApplyPatch(TransactionRecord transaction, IReadOnlyDictionary<string, object?> patch)
    {
        foreach (var item in patch)
        {
            switch (item.Key)
            {
                case nameof(TransactionRecord.Amount):
                    transaction.Amount = (decimal)item.Value!;
                    break;
                case nameof(TransactionRecord.Currency):
                    transaction.Currency = (string)item.Value!;
                    break;
                case nameof(TransactionRecord.TransactionName):
                    transaction.TransactionName = (string)item.Value!;
                    break;
                case nameof(TransactionRecord.Merchant):
                    transaction.Merchant = (string?)item.Value;
                    break;
                case nameof(TransactionRecord.CategoryId):
                    transaction.CategoryId = (Guid?)item.Value;
                    break;
                case nameof(TransactionRecord.OperationType):
                    transaction.OperationType = (string)item.Value!;
                    break;
                case nameof(TransactionRecord.OccurredAt):
                    transaction.OccurredAt = (DateTimeOffset)item.Value!;
                    break;
                case nameof(TransactionRecord.AvailableBalance):
                    transaction.AvailableBalance = (decimal)item.Value!;
                    break;
            }
        }
    }

    private static void MarkAllFieldsChanged(TransactionRecord transaction, long version)
    {
        var fields = new[]
        {
            nameof(TransactionRecord.Amount),
            nameof(TransactionRecord.Currency),
            nameof(TransactionRecord.TransactionName),
            nameof(TransactionRecord.Merchant),
            nameof(TransactionRecord.CategoryId),
            nameof(TransactionRecord.OperationType),
            nameof(TransactionRecord.OccurredAt),
            nameof(TransactionRecord.AvailableBalance),
            nameof(TransactionRecord.DeletedAt),
        };

        foreach (var field in fields)
        {
            transaction.FieldVersions[field] = version;
        }
    }

    private void WriteAudit(
        Guid householdId,
        Guid transactionId,
        Guid actorUserId,
        string action,
        long resultVersion,
        IReadOnlyList<string> changedFields,
        DateTimeOffset now)
    {
        auditLog.Add(new TransactionAuditRecord
        {
            Id = Guid.NewGuid(),
            HouseholdId = householdId,
            TransactionId = transactionId,
            ActorUserId = actorUserId,
            Action = action,
            ResultVersion = resultVersion,
            CreatedAt = now,
            ChangedFields = changedFields,
        });
    }

    private static HouseholdResponse ToHouseholdResponse(HouseholdRecord household, string role) =>
        new(household.Id, household.Name, household.BaseCurrency, role, household.CreatedAt);

    private static AccountResponse ToAccountResponse(AccountRecord account) =>
        new(
            account.Id,
            account.HouseholdId,
            account.Name,
            account.Currency,
            account.Type,
            account.BankName,
            account.AccountHint,
            account.CreatedAt);

    private static TransactionResponse ToTransactionResponse(TransactionRecord transaction) =>
        new(
            transaction.Id,
            transaction.HouseholdId,
            transaction.AccountId,
            transaction.CreatedBy,
            transaction.Amount,
            transaction.Currency,
            transaction.TransactionName,
            transaction.Merchant,
            transaction.CategoryId,
            transaction.Source,
            transaction.OperationType,
            transaction.OccurredAt,
            transaction.ReceivedAt,
            transaction.ClientCreatedAt,
            transaction.AvailableBalance,
            transaction.Version,
            transaction.UpdatedAt,
            transaction.DeletedAt,
            transaction.MergedIntoTransactionId,
            transaction.ExternalFingerprint);

    private void SeedSystemCategories()
    {
        var names = new[] { "Other", "Groceries", "Transport", "Subscriptions", "Pharmacy", "Restaurants" };
        foreach (var name in names)
        {
            categories.Add(new CategoryRecord
            {
                Id = Guid.NewGuid(),
                HouseholdId = null,
                Name = name,
                ParentId = null,
                IsSystem = true,
            });
        }

        bankTemplates.Add(new BankNotificationTemplateRecord
        {
            Id = Guid.NewGuid(),
            BankName = "Raiffeisen Serbia",
            Locale = "sr-Latn-RS",
            TitlePattern = "Moja mBanka",
            BodyPattern = "Koriscenje kartice * Mesto: *",
            ParserConfigJson = """{"amount":"known-parser","card":"last4-only","balance":"optional"}""",
            Version = 1,
        });

        _ = categoryRules;
        _ = aiResults;
    }

    private static string CreateOpaqueToken() =>
        Convert.ToBase64String(RandomNumberGenerator.GetBytes(32))
            .Replace("+", "-", StringComparison.Ordinal)
            .Replace("/", "_", StringComparison.Ordinal)
            .TrimEnd('=');

    private static string NormalizeEmail(string value) => value.Trim().ToLowerInvariant();

    private static string NormalizeCurrency(string value) => value.Trim().ToUpperInvariant();

    private static bool IsValidEmail(string value) => value.Contains('@', StringComparison.Ordinal) && value.Contains('.', StringComparison.Ordinal);

    private static string? BlankToNull(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return null;
        }

        return value.Trim();
    }
}
