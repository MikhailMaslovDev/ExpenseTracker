CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL,
    name text NOT NULL,
    password_hash text NOT NULL,
    password_salt text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT users_name_not_blank CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (lower(email));

CREATE TABLE IF NOT EXISTS sessions (
    token text PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_sessions_user_id ON sessions(user_id);

CREATE TABLE IF NOT EXISTS households (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    base_currency char(3) NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT households_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT households_base_currency_upper CHECK (base_currency = upper(base_currency))
);

CREATE TABLE IF NOT EXISTS household_members (
    household_id uuid NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role text NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, user_id),
    CONSTRAINT household_members_role_known CHECK (role IN ('Owner', 'Member'))
);

CREATE INDEX IF NOT EXISTS ix_household_members_user_id ON household_members(user_id);

CREATE TABLE IF NOT EXISTS invitations (
    token text PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    email text NOT NULL,
    expires_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_invitations_household_id ON invitations(household_id);
CREATE INDEX IF NOT EXISTS ix_invitations_email_lower ON invitations(lower(email));

CREATE TABLE IF NOT EXISTS accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id uuid NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    name text NOT NULL,
    currency char(3) NOT NULL,
    type text NOT NULL,
    bank_name text NULL,
    account_hint text NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT accounts_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT accounts_currency_upper CHECK (currency = upper(currency))
);

CREATE INDEX IF NOT EXISTS ix_accounts_household_id ON accounts(household_id);

CREATE TABLE IF NOT EXISTS categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id uuid NULL REFERENCES households(id) ON DELETE CASCADE,
    name text NOT NULL,
    parent_id uuid NULL REFERENCES categories(id),
    is_system boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT categories_name_not_blank CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_system_name
    ON categories (lower(name))
    WHERE household_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_household_name
    ON categories (household_id, lower(name))
    WHERE household_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS category_rules (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope text NOT NULL,
    household_id uuid NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id uuid NULL REFERENCES users(id) ON DELETE CASCADE,
    merchant_pattern text NOT NULL,
    category_id uuid NOT NULL REFERENCES categories(id),
    priority integer NOT NULL DEFAULT 100,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT category_rules_scope_known CHECK (scope IN ('User', 'Household', 'Global')),
    CONSTRAINT category_rules_pattern_not_blank CHECK (btrim(merchant_pattern) <> '')
);

CREATE INDEX IF NOT EXISTS ix_category_rules_lookup
    ON category_rules(scope, household_id, user_id, lower(merchant_pattern), priority);

CREATE TABLE IF NOT EXISTS bank_notification_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_name text NOT NULL,
    locale text NOT NULL,
    title_pattern text NOT NULL,
    body_pattern text NOT NULL,
    parser_config jsonb NOT NULL,
    version integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bank_notification_templates_version
    ON bank_notification_templates(bank_name, locale, version);

CREATE TABLE IF NOT EXISTS transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id uuid NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES accounts(id),
    created_by uuid NOT NULL REFERENCES users(id),
    amount numeric(18, 2) NOT NULL,
    currency char(3) NOT NULL,
    transaction_name text NOT NULL,
    merchant text NULL,
    category_id uuid NULL REFERENCES categories(id),
    source text NOT NULL,
    operation_type text NOT NULL,
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    client_created_at timestamptz NULL,
    available_balance numeric(18, 2) NULL,
    version bigint NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz NULL,
    merged_into_transaction_id uuid NULL REFERENCES transactions(id),
    external_fingerprint text NULL,
    CONSTRAINT transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT transactions_currency_upper CHECK (currency = upper(currency)),
    CONSTRAINT transactions_name_not_blank CHECK (btrim(transaction_name) <> '')
);

CREATE INDEX IF NOT EXISTS ix_transactions_household_sync
    ON transactions(household_id, version);

CREATE INDEX IF NOT EXISTS ix_transactions_household_occurred
    ON transactions(household_id, occurred_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_household_external_fingerprint
    ON transactions(household_id, external_fingerprint)
    WHERE external_fingerprint IS NOT NULL;

CREATE TABLE IF NOT EXISTS transaction_field_versions (
    transaction_id uuid NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    field_name text NOT NULL,
    version bigint NOT NULL,
    PRIMARY KEY (transaction_id, field_name)
);

CREATE TABLE IF NOT EXISTS transaction_audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id uuid NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    transaction_id uuid NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    actor_user_id uuid NOT NULL REFERENCES users(id),
    action text NOT NULL,
    result_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    changed_fields text[] NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_transaction_audit_log_transaction_id
    ON transaction_audit_log(transaction_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_categorization_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id uuid NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    predicted_category_id uuid NOT NULL REFERENCES categories(id),
    confidence numeric(5, 4) NOT NULL,
    model_version text NOT NULL,
    accepted_by_user boolean NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_categorization_confidence_range CHECK (confidence >= 0 AND confidence <= 1)
);

INSERT INTO categories(name, is_system, sort_order)
VALUES
    ('Other', true, 0),
    ('Groceries', true, 10),
    ('Transport', true, 20),
    ('Subscriptions', true, 30),
    ('Pharmacy', true, 40),
    ('Restaurants', true, 50)
ON CONFLICT DO NOTHING;

INSERT INTO bank_notification_templates(bank_name, locale, title_pattern, body_pattern, parser_config, version)
VALUES (
    'Raiffeisen Serbia',
    'sr-Latn-RS',
    'Moja mBanka',
    'Koriscenje kartice * Mesto: *',
    '{"amount":"known-parser","card":"last4-only","balance":"optional"}'::jsonb,
    1
)
ON CONFLICT DO NOTHING;
