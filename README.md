# Family Expense Tracker Android Prototype

Small local-first Android proof of concept for validating the first product risk:

```text
Raiffeisen Serbia push notification -> parsed fields on screen
```

The app:

- opens Android notification access settings;
- listens only to the official Raiffeisen Serbia package, `rs.Raiffeisen.mobile`;
- stores matching notifications locally in Room;
- shows raw text and parsed amount, currency, merchant, operation type, card hint, and date;
- shows the latest known balance for each card when it is present in a bank push;
- creates local card accounts automatically and lets the user assign friendly account names;
- suggests a local expense category for known merchants;
- keeps built-in and user-created categories locally in Room;
- allows renaming and deleting user-created categories while protecting built-in categories;
- allows editing and soft-deleting individual transactions;
- supports manual expense entry;
- filters the selected period by text and expense category;
- exports the currently visible transaction list to a UTF-8 CSV file without raw push text;
- creates and restores a versioned local JSON backup without raw push text;
- includes a first backend API slice for authentication, households, invitations, accounts,
  transaction sync, optimistic concurrency, and audit semantics;
- shows an expense summary grouped by category and top merchant names for a selected month or custom date range;
- imports currently active Raiffeisen notifications when the listener connects or the app opens;
- includes a manual **Scan active pushes** button for recovery checks;
- deduplicates repeated scans before storing a second local record;
- does not use a backend or send notification text anywhere.

## Run

On Windows, keep the repository in an ASCII-only path such as
`C:\dev\FamilyExpenseTracker`. Android build tools and JUnit workers can fail when the project
path contains Cyrillic characters.

1. Open the project in Android Studio.
2. Install Android SDK 35 if Android Studio asks for it.
3. Run the `app` configuration on an Android phone or emulator with API 26+.
4. Tap **Open notification access settings** and allow access for the prototype.
5. Wait for a real notification from Moja mBanka Raiffeisen or add a transaction manually.

The current parser is deliberately conservative and generic. After capturing real notification
examples, add anonymized variants to `RaiffeisenNotificationParserTest` and tune the parser rules.

## Local build note

The repository includes Gradle wrapper 8.10.2. A local build requires JDK 17 and Android SDK 35.

## Backend

The first backend slice lives in `backend/FamilyExpenseTracker.Api`. It is a .NET 10 API with
controllers. It can run against local PostgreSQL from Docker Compose, or fall back to the in-memory
prototype store when no connection string is provided.

See `backend/README.md`.

## Next milestones

1. Add PostgreSQL/EF Core persistence for backend users, households, accounts, transactions, and audit logs.
2. Connect the Android prototype to the backend sync contract with a local outbox and pull cursor.
3. Add duplicate candidate scoring and explicit merge UI.
