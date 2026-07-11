-- ============================================================================
-- IAM baseline: roles, users, outbox_events
-- ============================================================================

-- Roles -----------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO roles (id, code, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'USER',  'Regular user'),
    ('22222222-2222-2222-2222-222222222222', 'ADMIN', 'Administrator');

-- Users -----------------------------------------------------------------------
CREATE TABLE users (
    id                 UUID         PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    full_name          VARCHAR(100) NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    role_id            UUID         NOT NULL REFERENCES roles(id),
    email_verified_at  TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT users_status_check CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'ANONYMIZED'))
);

CREATE INDEX idx_users_status_pending_cleanup
    ON users (status, created_at)
    WHERE status = 'PENDING_VERIFICATION' AND deleted_at IS NULL;

CREATE INDEX idx_users_role_id ON users (role_id);

-- Outbox events ---------------------------------------------------------------
CREATE TABLE outbox_events (
    id                UUID         PRIMARY KEY,
    aggregate_type    VARCHAR(64)  NOT NULL,
    aggregate_id      UUID         NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    payload_key       VARCHAR(128),
    payload           TEXT         NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempt_count     INT          NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_error        TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMPTZ,
    CONSTRAINT outbox_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'DEAD_LETTER'))
);

CREATE INDEX idx_outbox_pending_retry
    ON outbox_events (next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);