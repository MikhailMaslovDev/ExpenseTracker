using FamilyExpenseTracker.Api.Models;
using FamilyExpenseTracker.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace FamilyExpenseTracker.Api.Controllers;

[Route("api/v1/auth")]
public sealed class AuthController : ApiControllerBase
{
    private readonly IExpenseStore store;

    public AuthController(IExpenseStore store)
    {
        this.store = store;
    }

    [HttpPost("register")]
    public ActionResult<AuthResponse> Register(RegisterRequest request) =>
        OkOrError(store.Register(request));

    [HttpPost("login")]
    public ActionResult<AuthResponse> Login(LoginRequest request) =>
        OkOrError(store.Login(request));
}
