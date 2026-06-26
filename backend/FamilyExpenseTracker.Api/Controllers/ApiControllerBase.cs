using FamilyExpenseTracker.Api.Infrastructure;
using FamilyExpenseTracker.Api.Models;
using Microsoft.AspNetCore.Mvc;
using System;

namespace FamilyExpenseTracker.Api.Controllers;

[ApiController]
public abstract class ApiControllerBase : ControllerBase
{
    protected bool TryGetCurrentUserId(out Guid userId)
    {
        if (HttpContext.Items.TryGetValue(AuthTokenMiddleware.UserIdItem, out var value) && value is Guid id)
        {
            userId = id;
            return true;
        }

        userId = Guid.Empty;
        return false;
    }

    protected ActionResult Error(ApiError error) =>
        error.Status switch
        {
            404 => NotFound(error),
            409 => Conflict(error),
            _ => BadRequest(error),
        };

    protected ActionResult<T> OkOrError<T>(Result<T> result) =>
        result.Error is null ? Ok(result.Value) : Error(result.Error);
}
