-- Account deletion by support (mvp.md §30, OQ-075, data-model.md §5): runbook, infrastructure/staging/README.md.
--   psql "<connection to the mbia database>" -v email=<the account's email> -f delete-account.sql
-- One transaction: nothing changes if a step fails. Refused while the User is the last ACTIVE ADMIN of a Family.
-- The Keycloak account is deleted afterwards, in the admin console (the subject is printed at the end).
\set ON_ERROR_STOP on

BEGIN;

SELECT set_config('mbia.delete_email', :'email', true);

DO $$
DECLARE
    v_user   users%ROWTYPE;
    v_now    TIMESTAMPTZ := clock_timestamp();
    v_last   TEXT;
    m        RECORD;
    p        RECORD;
    i        RECORD;
    n_member INT := 0;
    n_person INT := 0;
    n_invite INT := 0;
BEGIN
    SELECT * INTO v_user FROM users
    WHERE lower(email) = lower(current_setting('mbia.delete_email')) AND status = 'ACTIVE'
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No ACTIVE account with this email';
    END IF;

    -- A Family keeps an ACTIVE ADMIN (data-model.md §28 rule 12): lock its ADMIN memberships, as the use case does.
    PERFORM 1 FROM family_memberships
    WHERE role = 'ADMIN' AND status = 'ACTIVE'
      AND family_id IN (SELECT family_id FROM family_memberships WHERE user_id = v_user.id AND status = 'ACTIVE')
    ORDER BY id
    FOR UPDATE;

    SELECT string_agg(mine.family_id::TEXT, ', ') INTO v_last
    FROM family_memberships mine
    WHERE mine.user_id = v_user.id AND mine.status = 'ACTIVE' AND mine.role = 'ADMIN'
      AND NOT EXISTS (SELECT 1 FROM family_memberships other
                      WHERE other.family_id = mine.family_id AND other.status = 'ACTIVE'
                        AND other.role = 'ADMIN' AND other.user_id <> v_user.id);
    IF v_last IS NOT NULL THEN
        RAISE EXCEPTION 'Last ADMIN of the Families %: the User names another ADMIN first (OQ-075)', v_last;
    END IF;

    -- Memberships removed, linked Persons released (data-model.md §7), audited as by the application.
    FOR m IN SELECT * FROM family_memberships WHERE user_id = v_user.id AND status = 'ACTIVE' ORDER BY id FOR UPDATE LOOP
        UPDATE family_memberships
        SET status = 'REMOVED', removed_at = v_now, updated_at = v_now, version = version + 1
        WHERE id = m.id;
        INSERT INTO audit_entries (id, family_id, actor_user_id, action, resource_type, resource_id,
                                   old_value, new_value, occurred_at)
        VALUES (gen_random_uuid(), m.family_id, v_user.id, 'MEMBERSHIP_REMOVED', 'MEMBERSHIP', m.id,
                jsonb_build_object('status', m.status, 'role', m.role), jsonb_build_object('status', 'REMOVED'), v_now);
        n_member := n_member + 1;

        FOR p IN SELECT * FROM persons
                 WHERE family_id = m.family_id AND linked_user_id = v_user.id AND status <> 'MERGED'
                 FOR UPDATE LOOP
            UPDATE persons
            SET linked_user_id = NULL, updated_by = v_user.id, updated_at = v_now, version = version + 1
            WHERE id = p.id;
            INSERT INTO audit_entries (id, family_id, actor_user_id, action, resource_type, resource_id,
                                       old_value, new_value, occurred_at)
            VALUES (gen_random_uuid(), m.family_id, v_user.id, 'PERSON_UNCLAIMED', 'PERSON', p.id,
                    jsonb_build_object('linkedUserId', v_user.id), '{}'::JSONB, v_now);
            n_person := n_person + 1;
        END LOOP;
    END LOOP;

    -- Pending invitations sent by the User or to the User's email: revoked.
    FOR i IN SELECT * FROM family_invitations
             WHERE status = 'PENDING' AND (invited_by = v_user.id OR lower(email) = lower(v_user.email))
             ORDER BY id
             FOR UPDATE LOOP
        UPDATE family_invitations
        SET status = 'REVOKED', revoked_by = v_user.id, revoked_at = v_now, updated_at = v_now, version = version + 1
        WHERE id = i.id;
        INSERT INTO audit_entries (id, family_id, actor_user_id, action, resource_type, resource_id,
                                   old_value, new_value, occurred_at)
        VALUES (gen_random_uuid(), i.family_id, v_user.id, 'INVITATION_REVOKED', 'INVITATION', i.id,
                jsonb_strip_nulls(jsonb_build_object('channel', i.channel, 'role', i.role, 'personId', i.person_id,
                                                     'status', i.status, 'expiresAt', to_char(i.expires_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))),
                jsonb_strip_nulls(jsonb_build_object('channel', i.channel, 'role', i.role, 'personId', i.person_id,
                                                     'status', 'REVOKED', 'expiresAt', to_char(i.expires_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))),
                v_now);
        n_invite := n_invite + 1;
    END LOOP;

    -- The User anonymised (data-model.md §5): shown as "Former member". The audit holds no personal data.
    UPDATE users
    SET status = 'DELETED', email = NULL, display_name = NULL, identity_provider_subject = 'deleted:' || id,
        deleted_at = v_now, updated_at = v_now, version = version + 1
    WHERE id = v_user.id;
    INSERT INTO audit_entries (id, family_id, actor_user_id, action, resource_type, resource_id,
                               old_value, new_value, occurred_at)
    VALUES (gen_random_uuid(), NULL, v_user.id, 'USER_DELETED', 'USER', v_user.id,
            jsonb_build_object('status', 'ACTIVE'), jsonb_build_object('status', 'DELETED'), v_now);

    RAISE NOTICE 'User % anonymised: % memberships removed, % Persons released, % invitations revoked',
        v_user.id, n_member, n_person, n_invite;
    RAISE NOTICE 'Delete the Keycloak user whose ID is %', v_user.identity_provider_subject;
END
$$;

COMMIT;
