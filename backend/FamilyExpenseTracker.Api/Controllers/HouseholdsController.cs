using FamilyExpenseTracker.Api.Models;
using FamilyExpenseTracker.Api.Services;
using Microsoft.AspNetCore.Mvc;
using System;
using System.Collections.Generic;

namespace FamilyExpenseTracker.Api.Controllers;

[Route("api/v1/households")]
public sealed class HouseholdsController : ApiControllerBase
{
    private readonly IExpenseStore store;

    public HouseholdsController(IExpenseStore store)
    {
        this.store = store;
    }

    [HttpGet]
    public ActionResult<IReadOnlyList<HouseholdResponse>> GetHouseholds()
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        return Ok(store.GetHouseholds(userId));
    }

    [HttpPost]
    public ActionResult<HouseholdResponse> CreateHousehold(CreateHouseholdRequest request)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        var result = store.CreateHousehold(userId, request);
        if (result.Error is not null)
        {
            return Error(result.Error);
        }

        return Created($"/api/v1/households/{result.Value!.Id}", result.Value);
    }

    [HttpPost("{householdId:guid}/invitations")]
    public ActionResult<InvitationResponse> CreateInvitation(Guid householdId, CreateInvitationRequest request)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        return OkOrError(store.CreateInvitation(userId, householdId, request));
    }
}
