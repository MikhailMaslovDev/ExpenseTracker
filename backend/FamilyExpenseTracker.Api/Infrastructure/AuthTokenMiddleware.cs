using FamilyExpenseTracker.Api.Services;

namespace FamilyExpenseTracker.Api.Infrastructure;

public sealed class AuthTokenMiddleware
{
    public const string UserIdItem = "UserId";

    private readonly RequestDelegate next;

    public AuthTokenMiddleware(RequestDelegate next)
    {
        this.next = next;
    }

    public async Task InvokeAsync(HttpContext context, IExpenseStore store)
    {
        var header = context.Request.Headers.Authorization.ToString();

        if (header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) &&
            store.TryGetUserIdForToken(header["Bearer ".Length..].Trim(), out var userId))
        {
            context.Items[UserIdItem] = userId;
        }

        await next(context);
    }
}
