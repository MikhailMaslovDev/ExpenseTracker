namespace FamilyExpenseTracker.Api.Data;

public sealed class PostgresOptions
{
    public required string ConnectionString { get; init; }

    public required string MigrationsPath { get; init; }
}
