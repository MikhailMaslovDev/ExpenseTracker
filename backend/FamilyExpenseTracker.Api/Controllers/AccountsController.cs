using FamilyExpenseTracker.Api.Models;
using FamilyExpenseTracker.Api.Services;
using Microsoft.AspNetCore.Mvc;
using System;
using System.Collections.Generic;

namespace FamilyExpenseTracker.Api.Controllers;

[Route("api/v1/households/{householdId:guid}/accounts")]
public sealed class AccountsController : ApiControllerBase
{
    private readonly IExpenseStore store;

    public AccountsController(IExpenseStore store)
    {
        this.store = store;
    }

    [HttpGet]
    public ActionResult<IReadOnlyList<AccountResponse>> GetAccounts(Guid householdId)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        return OkOrError(store.GetAccounts(userId, householdId));
    }

    [HttpPost]
    public ActionResult<AccountResponse> CreateAccount(Guid householdId, CreateAccountRequest request)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        var result = store.CreateAccount(userId, householdId, request);
        if (result.Error is not null)
        {
            return Error(result.Error);
        }

        return Created($"/api/v1/households/{householdId}/accounts/{result.Value!.Id}", result.Value);
    }
}
