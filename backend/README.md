# Family Expense Tracker Backend

This is the first backend slice for the shared-budget roadmap. The HTTP layer is organized with
controllers. At runtime the API uses PostgreSQL when `ConnectionStrings__Default` is set, and falls
back to the in-memory prototype store when it is not.

## What This Slice Covers

- email/password registration and login with salted PBKDF2 password hashes;
- bearer-style opaque sessions for local development;
- households with owner/member roles;
- invite-by-token flow, suitable for email or link invitations later;
- household accounts/wallets;
- structured transaction records instead of one encrypted wallet JSON blob;
- transaction sync by per-household cursor version;
- optimistic concurrency with field-level merge detection;
- soft delete and audit events;
- placeholders in the domain model for notification templates, category rules, and AI results;
- SQL-first persistence design without EF: tables, indexes, constraints, and sync functions.

## Design Notes From The Interview

Kept:

- server is the source of truth for shared households;
- transactions are synced as individual records with `version`, `updatedAt`, and `deletedAt`;
- Android notification import should create candidate transactions locally and sync structured fields;
- user corrections become user/household category rules before AI is used;
- audit/history matters for shared edits;
- privacy: do not persist raw notification text or raw AI prompts by default.

Rejected or deferred:

- no encrypted JSON wallet blob in MongoDB; reporting and conflict handling need structured records;
- no silent deletion for duplicates; use fingerprint dedupe and later add merge suggestions;
- no production auth/JWT yet; the current opaque token is a dev implementation.

## Run API With PostgreSQL

Start local PostgreSQL:

```powershell
docker compose up -d family-expense-db
```

Run the API against the Docker database:

```powershell
$env:ConnectionStrings__Default='Host=127.0.0.1;Port=54329;Database=family_expenses;Username=family_expenses;Password=family_expenses_dev'
dotnet run --project backend\FamilyExpenseTracker.Api\FamilyExpenseTracker.Api.csproj --urls "http://127.0.0.1:5062"
```

The API applies SQL migrations on startup.

## Test From Android

Install the debug APK, open the app menu, then choose **Test backend connection**.

- Android emulator: keep the default `http://10.0.2.2:5062`.
- Real phone: use your computer IP on the same Wi-Fi network, for example
  `http://192.168.1.10:5062`.

The check calls `/health` and shows a toast when the backend is reachable.

## Run API In Memory

If `ConnectionStrings__Default` is not set, the API uses the in-memory prototype store:

```powershell
dotnet run --project backend\FamilyExpenseTracker.Api\FamilyExpenseTracker.Api.csproj
```

Then check:

```powershell
Invoke-RestMethod http://localhost:5000/health
```

Depending on local .NET launch settings, the port may be printed by `dotnet run`.

## Database Direction

We are intentionally not using EF. Database work goes through SQL scripts and later a thin `Npgsql`
data access layer.

Current scripts:

- `backend/database/migrations/001_initial_schema.sql`
- `backend/database/migrations/002_sync_functions.sql`

They define structured records for users, households, members, accounts, categories, category
rules, notification templates, transactions, transaction field versions, audit logs, and AI
categorization results. Sync-critical operations are modeled as PostgreSQL functions.

## Next Backend Steps

1. Add SQL function for transaction field-level update conflict resolution.
2. Split `PostgresExpenseStore` into focused repositories as the API grows.
3. Replace opaque dev sessions with JWT access tokens plus refresh-token rotation.
4. Add mobile sync client code on Android: local outbox, pull `sinceVersion`, push local changes.
5. Add duplicate candidate scoring and explicit merge UI.
6. Add AI categorization service behind rules/cache and sanitized request payloads.
