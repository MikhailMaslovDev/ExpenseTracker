using FamilyExpenseTracker.Api.Models;
using FamilyExpenseTracker.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace FamilyExpenseTracker.Api.Controllers;

[Route("api/v1/invitations")]
public sealed class InvitationsController : ApiControllerBase
{
    private readonly IExpenseStore store;

    public InvitationsController(IExpenseStore store)
    {
        this.store = store;
    }

    [HttpPost("{token}/accept")]
    public ActionResult<HouseholdResponse> AcceptInvitation(string token)
    {
        if (!TryGetCurrentUserId(out var userId))
        {
            return Unauthorized();
        }

        return OkOrError(store.AcceptInvitation(userId, token));
    }
}
