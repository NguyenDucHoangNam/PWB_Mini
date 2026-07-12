-- ============================================================================
-- V13: BaseEntity migration — add audit + version columns, drop legacy
-- `users.deleted` boolean (soft-delete now driven solely by `deleted_at`).
-- ============================================================================

-- users -----------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN created_by VARCHAR(64),
    ADD COLUMN updated_by VARCHAR(64),
    ADD COLUMN deleted_by VARCHAR(64),
    ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

UPDATE users
SET created_by = COALESCE(created_by, 'SYSTEM'),
    updated_by = COALESCE(updated_by, 'SYSTEM');

ALTER TABLE users
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE users
    DROP COLUMN deleted;

-- roles -----------------------------------------------------------------------
ALTER TABLE roles
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN created_by VARCHAR(64),
    ADD COLUMN updated_by VARCHAR(64),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by VARCHAR(64),
    ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

UPDATE roles
SET created_by = COALESCE(created_by, 'SYSTEM'),
    updated_by = COALESCE(updated_by, 'SYSTEM');

ALTER TABLE roles
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;

-- outbox_events ---------------------------------------------------------------
ALTER TABLE outbox_events
    ADD COLUMN created_by VARCHAR(64),
    ADD COLUMN updated_by VARCHAR(64),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by VARCHAR(64),
    ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

UPDATE outbox_events
SET created_by = COALESCE(created_by, 'SYSTEM'),
    updated_by = COALESCE(updated_by, 'SYSTEM');

ALTER TABLE outbox_events
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;

-- Suggested indexes for soft-delete + audit queries ----------------------------
CREATE INDEX idx_users_deleted_at ON users (deleted_at);
CREATE INDEX idx_roles_deleted_at ON roles (deleted_at);
CREATE INDEX idx_outbox_deleted_at ON outbox_events (deleted_at);
CREATE INDEX idx_users_updated_by ON users (updated_by);
CREATE INDEX idx_users_created_by ON users (created_by);
