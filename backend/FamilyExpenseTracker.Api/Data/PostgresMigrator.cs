using Npgsql;

namespace FamilyExpenseTracker.Api.Data;

public sealed class PostgresMigrator
{
    private readonly PostgresOptions options;
    private readonly ILogger<PostgresMigrator> logger;

    public PostgresMigrator(PostgresOptions options, ILogger<PostgresMigrator> logger)
    {
        this.options = options;
        this.logger = logger;
    }

    public void ApplyMigrations()
    {
        using var connection = new NpgsqlConnection(options.ConnectionString);
        connection.Open();

        using (var command = connection.CreateCommand())
        {
            command.CommandText = """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    file_name text PRIMARY KEY,
                    applied_at timestamptz NOT NULL DEFAULT now()
                );
                """;
            command.ExecuteNonQuery();
        }

        var migrationFiles = Directory
            .EnumerateFiles(options.MigrationsPath, "*.sql")
            .OrderBy(path => path, StringComparer.OrdinalIgnoreCase)
            .ToList();

        foreach (var file in migrationFiles)
        {
            var fileName = Path.GetFileName(file);
            if (HasMigration(connection, fileName))
            {
                continue;
            }

            logger.LogInformation("Applying database migration {Migration}", fileName);

            using var transaction = connection.BeginTransaction();
            using var migrationCommand = connection.CreateCommand();
            migrationCommand.Transaction = transaction;
            migrationCommand.CommandText = File.ReadAllText(file);
            migrationCommand.ExecuteNonQuery();

            using var markerCommand = connection.CreateCommand();
            markerCommand.Transaction = transaction;
            markerCommand.CommandText = "INSERT INTO schema_migrations(file_name) VALUES (@file_name);";
            markerCommand.Parameters.AddWithValue("file_name", fileName);
            markerCommand.ExecuteNonQuery();

            transaction.Commit();
        }
    }

    private static bool HasMigration(NpgsqlConnection connection, string fileName)
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT EXISTS (SELECT 1 FROM schema_migrations WHERE file_name = @file_name);";
        command.Parameters.AddWithValue("file_name", fileName);
        return (bool)command.ExecuteScalar()!;
    }
}
