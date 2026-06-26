CREATE OR REPLACE FUNCTION api_assert_household_member(
    p_user_id uuid,
    p_household_id uuid
) RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM household_members
        WHERE user_id = p_user_id
          AND household_id = p_household_id
    ) THEN
        RAISE EXCEPTION 'household_not_found'
            USING ERRCODE = 'P0001';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION api_next_household_version(
    p_household_id uuid
) RETURNS bigint
LANGUAGE sql
AS $$
    SELECT COALESCE(MAX(version), 0) + 1
    FROM transactions
    WHERE household_id = p_household_id;
$$;

CREATE OR REPLACE FUNCTION api_create_household(
    p_user_id uuid,
    p_name text,
    p_base_currency text
) RETURNS TABLE (
    id uuid,
    name text,
    base_currency char(3),
    role text,
    created_at timestamptz
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_household_id uuid;
    v_created_at timestamptz := now();
BEGIN
    INSERT INTO households(name, base_currency, created_by, created_at)
    VALUES (btrim(p_name), upper(p_base_currency)::char(3), p_user_id, v_created_at)
    RETURNING households.id INTO v_household_id;

    INSERT INTO household_members(household_id, user_id, role, joined_at)
    VALUES (v_household_id, p_user_id, 'Owner', v_created_at);

    INSERT INTO accounts(household_id, name, currency, type, created_at)
    VALUES (v_household_id, 'Main account', upper(p_base_currency)::char(3), 'Cash', v_created_at);

    RETURN QUERY
    SELECT h.id, h.name, h.base_currency, 'Owner'::text, h.created_at
    FROM households h
    WHERE h.id = v_household_id;
END;
$$;

CREATE OR REPLACE FUNCTION api_create_transaction(
    p_user_id uuid,
    p_household_id uuid,
    p_client_id uuid,
    p_account_id uuid,
    p_amount numeric,
    p_currency text,
    p_transaction_name text,
    p_merchant text,
    p_category_id uuid,
    p_source text,
    p_operation_type text,
    p_occurred_at timestamptz,
    p_client_created_at timestamptz,
    p_available_balance numeric,
    p_external_fingerprint text
) RETURNS SETOF transactions
LANGUAGE plpgsql
AS $$
DECLARE
    v_transaction_id uuid := COALESCE(p_client_id, gen_random_uuid());
    v_version bigint;
    v_now timestamptz := now();
    v_existing transactions%ROWTYPE;
    v_fields text[] := ARRAY[
        'Amount',
        'Currency',
        'TransactionName',
        'Merchant',
        'CategoryId',
        'OperationType',
        'OccurredAt',
        'AvailableBalance',
        'DeletedAt'
    ];
BEGIN
    PERFORM api_assert_household_member(p_user_id, p_household_id);

    IF NOT EXISTS (
        SELECT 1
        FROM accounts
        WHERE id = p_account_id
          AND household_id = p_household_id
    ) THEN
        RAISE EXCEPTION 'account_not_found'
            USING ERRCODE = 'P0001';
    END IF;

    IF p_external_fingerprint IS NOT NULL THEN
        SELECT *
        INTO v_existing
        FROM transactions
        WHERE household_id = p_household_id
          AND external_fingerprint = p_external_fingerprint
        LIMIT 1;

        IF FOUND THEN
            RETURN NEXT v_existing;
            RETURN;
        END IF;
    END IF;

    v_version := api_next_household_version(p_household_id);

    INSERT INTO transactions(
        id,
        household_id,
        account_id,
        created_by,
        amount,
        currency,
        transaction_name,
        merchant,
        category_id,
        source,
        operation_type,
        occurred_at,
        received_at,
        client_created_at,
        available_balance,
        version,
        updated_at,
        external_fingerprint
    )
    VALUES (
        v_transaction_id,
        p_household_id,
        p_account_id,
        p_user_id,
        p_amount,
        upper(p_currency)::char(3),
        btrim(p_transaction_name),
        nullif(btrim(p_merchant), ''),
        p_category_id,
        btrim(p_source),
        btrim(p_operation_type),
        p_occurred_at,
        v_now,
        p_client_created_at,
        p_available_balance,
        v_version,
        v_now,
        nullif(btrim(p_external_fingerprint), '')
    );

    INSERT INTO transaction_field_versions(transaction_id, field_name, version)
    SELECT v_transaction_id, field_name, v_version
    FROM unnest(v_fields) AS field_name;

    INSERT INTO transaction_audit_log(
        household_id,
        transaction_id,
        actor_user_id,
        action,
        result_version,
        changed_fields
    )
    VALUES (p_household_id, v_transaction_id, p_user_id, 'Created', v_version, v_fields);

    RETURN QUERY
    SELECT *
    FROM transactions
    WHERE transactions.id = v_transaction_id;
END;
$$;

CREATE OR REPLACE FUNCTION api_get_transactions_since(
    p_user_id uuid,
    p_household_id uuid,
    p_since_version bigint DEFAULT 0
) RETURNS SETOF transactions
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM api_assert_household_member(p_user_id, p_household_id);

    RETURN QUERY
    SELECT *
    FROM transactions
    WHERE household_id = p_household_id
      AND version > COALESCE(p_since_version, 0)
    ORDER BY updated_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION api_delete_transaction(
    p_user_id uuid,
    p_household_id uuid,
    p_transaction_id uuid,
    p_base_version bigint
) RETURNS SETOF transactions
LANGUAGE plpgsql
AS $$
DECLARE
    v_current transactions%ROWTYPE;
    v_deleted_field_version bigint;
    v_version bigint;
    v_now timestamptz := now();
BEGIN
    PERFORM api_assert_household_member(p_user_id, p_household_id);

    SELECT *
    INTO v_current
    FROM transactions
    WHERE id = p_transaction_id
      AND household_id = p_household_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'transaction_not_found'
            USING ERRCODE = 'P0001';
    END IF;

    SELECT version
    INTO v_deleted_field_version
    FROM transaction_field_versions
    WHERE transaction_id = p_transaction_id
      AND field_name = 'DeletedAt';

    IF v_deleted_field_version > p_base_version THEN
        RAISE EXCEPTION 'transaction_conflict:DeletedAt'
            USING ERRCODE = 'P0001';
    END IF;

    v_version := api_next_household_version(p_household_id);

    UPDATE transactions
    SET deleted_at = v_now,
        updated_at = v_now,
        version = v_version
    WHERE id = p_transaction_id;

    INSERT INTO transaction_field_versions(transaction_id, field_name, version)
    VALUES (p_transaction_id, 'DeletedAt', v_version)
    ON CONFLICT (transaction_id, field_name)
    DO UPDATE SET version = EXCLUDED.version;

    INSERT INTO transaction_audit_log(
        household_id,
        transaction_id,
        actor_user_id,
        action,
        result_version,
        changed_fields
    )
    VALUES (p_household_id, p_transaction_id, p_user_id, 'Deleted', v_version, ARRAY['DeletedAt']);

    RETURN QUERY
    SELECT *
    FROM transactions
    WHERE id = p_transaction_id;
END;
$$;
