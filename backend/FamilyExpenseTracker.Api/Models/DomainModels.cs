using System;
using System.Collections.Generic;

namespace FamilyExpenseTracker.Api.Models;

internal sealed class UserRecord
{
    public required Guid Id { get; init; }
    public required string Email { get; init; }
    public required string Name { get; set; }
    public required string PasswordHash { get; init; }
    public required string PasswordSalt { get; init; }
    public required DateTimeOffset CreatedAt { get; init; }
}

internal sealed class SessionRecord
{
    public required string Token { get; init; }
    public required Guid UserId { get; init; }
    public required DateTimeOffset ExpiresAt { get; init; }
}

internal sealed class HouseholdRecord
{
    public required Guid Id { get; init; }
    public required string Name { get; set; }
    public required string BaseCurrency { get; init; }
    public required Guid CreatedBy { get; init; }
    public required DateTimeOffset CreatedAt { get; init; }
}

internal sealed class HouseholdMemberRecord
{
    public required Guid HouseholdId { get; init; }
    public required Guid UserId { get; init; }
    public required string Role { get; init; }
    public required DateTimeOffset JoinedAt { get; init; }
}

internal sealed class InvitationRecord
{
    public required Guid HouseholdId { get; init; }
    public required string Email { get; init; }
    public required string Token { get; init; }
    public required DateTimeOffset ExpiresAt { get; init; }
    public required Guid CreatedBy { get; init; }
}

internal sealed class AccountRecord
{
    public required Guid Id { get; init; }
    public required Guid HouseholdId { get; init; }
    public required string Name { get; set; }
    public required string Currency { get; init; }
    public required string Type { get; init; }
    public string? BankName { get; init; }
    public string? AccountHint { get; init; }
    public required DateTimeOffset CreatedAt { get; init; }
}

internal sealed class CategoryRecord
{
    public required Guid Id { get; init; }
    public Guid? HouseholdId { get; init; }
    public required string Name { get; set; }
    public Guid? ParentId { get; init; }
    public required bool IsSystem { get; init; }
}

internal sealed class CategoryRuleRecord
{
    public required Guid Id { get; init; }
    public Guid? HouseholdId { get; init; }
    public Guid? UserId { get; init; }
    public required string Pattern { get; init; }
    public required Guid CategoryId { get; init; }
    public required int Priority { get; init; }
    public required DateTimeOffset UpdatedAt { get; init; }
}

internal sealed class BankNotificationTemplateRecord
{
    public required Guid Id { get; init; }
    public required string BankName { get; init; }
    public required string Locale { get; init; }
    public required string TitlePattern { get; init; }
    public required string BodyPattern { get; init; }
    public required string ParserConfigJson { get; init; }
    public required int Version { get; init; }
}

internal sealed class AiCategorizationResultRecord
{
    public required Guid Id { get; init; }
    public required Guid TransactionId { get; init; }
    public required Guid PredictedCategoryId { get; init; }
    public required decimal Confidence { get; init; }
    public required string ModelVersion { get; init; }
    public bool? AcceptedByUser { get; set; }
    public required DateTimeOffset CreatedAt { get; init; }
}

internal sealed class TransactionRecord
{
    public required Guid Id { get; init; }
    public required Guid HouseholdId { get; init; }
    public required Guid AccountId { get; init; }
    public required Guid CreatedBy { get; init; }
    public required decimal Amount { get; set; }
    public required string Currency { get; set; }
    public required string TransactionName { get; set; }
    public string? Merchant { get; set; }
    public Guid? CategoryId { get; set; }
    public required string Source { get; init; }
    public required string OperationType { get; set; }
    public required DateTimeOffset OccurredAt { get; set; }
    public required DateTimeOffset ReceivedAt { get; init; }
    public DateTimeOffset? ClientCreatedAt { get; init; }
    public decimal? AvailableBalance { get; set; }
    public required long Version { get; set; }
    public required DateTimeOffset UpdatedAt { get; set; }
    public DateTimeOffset? DeletedAt { get; set; }
    public Guid? MergedIntoTransactionId { get; set; }
    public string? ExternalFingerprint { get; init; }
    public required Dictionary<string, long> FieldVersions { get; init; }
}

internal sealed class TransactionAuditRecord
{
    public required Guid Id { get; init; }
    public required Guid HouseholdId { get; init; }
    public required Guid TransactionId { get; init; }
    public required Guid ActorUserId { get; init; }
    public required string Action { get; init; }
    public required long ResultVersion { get; init; }
    public required DateTimeOffset CreatedAt { get; init; }
    public required IReadOnlyList<string> ChangedFields { get; init; }
}
