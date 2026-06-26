using FamilyExpenseTracker.Api.Models;
using FamilyExpenseTracker.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace FamilyExpenseTracker.Api.Controllers;

[Route("api/v1/households/{householdId:guid}/transactions")]
public sealed class TransactionsController : ApiControllerBase
{
    private readonly IExpenseStore store;

    public TransactionsController(IExpenseStore store)
    {
        this.store = store;
    }

    [HttpGet]
    public ActionResult<TransactionSyncResponse> GetTransactions(Guid householdId, [FromQuery] long? sinceVersion)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        return OkOrError(store.GetTransactions(userId, householdId, sinceVersion));
    }

    [HttpPost]
    public ActionResult<TransactionResponse> CreateTransaction(Guid householdId, CreateTransactionRequest request)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        var result = store.CreateTransaction(userId, householdId, request);
        if (result.Error is not null)
        {
            return Error(result.Error);
        }

        return Created($"/api/v1/households/{householdId}/transactions/{result.Value!.Id}", result.Value);
    }

    [HttpPatch("{transactionId:guid}")]
    public ActionResult<TransactionResponse> UpdateTransaction(
        Guid householdId,
        Guid transactionId,
        UpdateTransactionRequest request)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        return OkOrError(store.UpdateTransaction(userId, householdId, transactionId, request));
    }

    [HttpDelete("{transactionId:guid}")]
    public ActionResult<TransactionResponse> DeleteTransaction(
        Guid householdId,
        Guid transactionId,
        DeleteTransactionRequest request)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        return OkOrError(store.DeleteTransaction(userId, householdId, transactionId, request));
    }
}
