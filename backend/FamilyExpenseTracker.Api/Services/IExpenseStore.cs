using FamilyExpenseTracker.Api.Models;

namespace FamilyExpenseTracker.Api.Services;

public interface IExpenseStore
{
    Result<AuthResponse> Register(RegisterRequest request);

    Result<AuthResponse> Login(LoginRequest request);

    bool TryGetUserIdForToken(string token, out Guid userId);

    IReadOnlyList<HouseholdResponse> GetHouseholds(Guid userId);

    Result<HouseholdResponse> CreateHousehold(Guid userId, CreateHouseholdRequest request);

    Result<InvitationResponse> CreateInvitation(Guid userId, Guid householdId, CreateInvitationRequest request);

    Result<HouseholdResponse> AcceptInvitation(Guid userId, string token);

    Result<IReadOnlyList<AccountResponse>> GetAccounts(Guid userId, Guid householdId);

    Result<AccountResponse> CreateAccount(Guid userId, Guid householdId, CreateAccountRequest request);

    Result<TransactionSyncResponse> GetTransactions(Guid userId, Guid householdId, long? sinceVersion);

    Result<TransactionResponse> CreateTransaction(Guid userId, Guid householdId, CreateTransactionRequest request);

    Result<TransactionResponse> UpdateTransaction(Guid userId, Guid householdId, Guid transactionId, UpdateTransactionRequest request);

    Result<TransactionResponse> DeleteTransaction(Guid userId, Guid householdId, Guid transactionId, DeleteTransactionRequest request);
}
