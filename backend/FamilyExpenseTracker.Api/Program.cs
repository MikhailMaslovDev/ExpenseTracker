using FamilyExpenseTracker.Api.Data;
using FamilyExpenseTracker.Api.Infrastructure;
using FamilyExpenseTracker.Api.Services;
using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

var builder = WebApplication.CreateBuilder(args);

builder.Logging.ClearProviders();
builder.Logging.AddConsole();

builder.Services.AddControllers();

var connectionString = builder.Configuration.GetConnectionString("Default");
if (string.IsNullOrWhiteSpace(connectionString))
{
    builder.Services.AddSingleton<IExpenseStore, ExpenseStore>();
}
else
{
    var migrationsPath = Path.GetFullPath(Path.Combine(
        builder.Environment.ContentRootPath,
        "..",
        "database",
        "migrations"));
    var postgresOptions = new PostgresOptions
    {
        ConnectionString = connectionString,
        MigrationsPath = migrationsPath,
    };

    builder.Services.AddSingleton(postgresOptions);
    builder.Services.AddSingleton<IExpenseStore>(_ => new PostgresExpenseStore(connectionString));
    builder.Services.AddSingleton<PostgresMigrator>();
}

var app = builder.Build();

var migrator = app.Services.GetService<PostgresMigrator>();
migrator?.ApplyMigrations();

app.UseMiddleware<AuthTokenMiddleware>();
app.MapControllers();

app.Run();
