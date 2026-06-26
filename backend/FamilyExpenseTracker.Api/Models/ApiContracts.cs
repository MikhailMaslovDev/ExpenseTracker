using Microsoft.AspNetCore.Http;
using System;
using System.Collections.Generic;

namespace FamilyExpenseTracker.Api.Models;

public sealed record HealthResponse(string Status, DateTimeOffset CheckedAt);

public sealed record RegisterRequest(string Email, string Password, string Name);

public sealed record LoginRequest(string Email, string Password);

public sealed record AuthResponse(Guid UserId, string Email, string Name, string AccessToken, DateTimeOffset ExpiresAt);

public sealed record CreateHouseholdRequest(string Name, string BaseCurrency);

public sealed record HouseholdResponse(Guid Id, string Name, string BaseCurrency, string Role, DateTimeOffset CreatedAt);

public sealed record CreateInvitationRequest(string Email);

public sealed record InvitationResponse(Guid HouseholdId, string Email, string Token, DateTimeOffset ExpiresAt);

public sealed record CreateAccountRequest(
    string Name,
    string Currency,
    string Type,
    string? BankName,
    string? AccountHint);

public sealed record AccountResponse(
    Guid Id,
    Guid HouseholdId,
    string Name,
    string Currency,
    string Type,
    string? BankName,
    string? AccountHint,
    DateTimeOffset CreatedAt);

public sealed record CreateTransactionRequest(
    Guid? ClientId,
    Guid AccountId,
    decimal Amount,
    string Currency,
    string TransactionName,
    string? Merchant,
    Guid? CategoryId,
    string Source,
    string OperationType,
    DateTimeOffset OccurredAt,
    DateTimeOffset? ClientCreatedAt,
    decimal? AvailableBalance,
    string? ExternalFingerprint);

public sealed record UpdateTransactionRequest(
    long BaseVersion,
    decimal? Amount,
    string? Currency,
    string? TransactionName,
    string? Merchant,
    Guid? CategoryId,
    string? OperationType,
    DateTimeOffset? OccurredAt,
    decimal? AvailableBalance);

public sealed record DeleteTransactionRequest(long BaseVersion);

public sealed record TransactionResponse(
    Guid Id,
    Guid HouseholdId,
    Guid AccountId,
    Guid CreatedBy,
    decimal Amount,
    string Currency,
    string TransactionName,
    string? Merchant,
    Guid? CategoryId,
    string Source,
    string OperationType,
    DateTimeOffset OccurredAt,
    DateTimeOffset ReceivedAt,
    DateTimeOffset? ClientCreatedAt,
    decimal? AvailableBalance,
    long Version,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DeletedAt,
    Guid? MergedIntoTransactionId,
    string? ExternalFingerprint);

public sealed record TransactionSyncResponse(
    Guid HouseholdId,
    long CursorVersion,
    IReadOnlyList<TransactionResponse> Transactions);

public sealed record ApiError(int Status, string Code, string Message, IReadOnlyList<string>? ConflictFields = null);

public readonly record struct Result<T>(T? Value, ApiError? Error)
{
    public static Result<T> Success(T value) => new(value, null);

    public static Result<T> Failure(int status, string code, string message, IReadOnlyList<string>? conflictFields = null) =>
        new(default, new ApiError(status, code, message, conflictFields));

    public IResult Match(Func<T, IResult> onSuccess, Func<ApiError, IResult> onFailure) =>
        Error is null ? onSuccess(Value!) : onFailure(Error);
}
