using FamilyExpenseTracker.Api.Models;
using Microsoft.AspNetCore.Mvc;
using System;

namespace FamilyExpenseTracker.Api.Controllers;

[Route("health")]
public sealed class HealthController : ApiControllerBase
{
    [HttpGet]
    public ActionResult<HealthResponse> Get() =>
        Ok(new HealthResponse("ok", DateTimeOffset.UtcNow));
}
