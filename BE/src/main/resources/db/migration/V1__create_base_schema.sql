
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(100) NOT NULL DEFAULT 'system',
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE users (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username               VARCHAR(50)  NOT NULL UNIQUE,
    email                  VARCHAR(255) NOT NULL UNIQUE,
    password               VARCHAR(255),
    full_name              VARCHAR(100),
    avatar_url             VARCHAR(500),
    phone                  VARCHAR(20),
    status                 VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role_id                UUID         REFERENCES roles(id),
    oauth_provider         VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    oauth_id               VARCHAR(255),
    deletion_requested_at  TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by             VARCHAR(100) NOT NULL DEFAULT 'system',
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at             TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE outbox_events (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100)  NOT NULL,
    aggregate_id    VARCHAR(255)  NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    idempotency_key VARCHAR(255)  NOT NULL UNIQUE,
    payload         TEXT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(100)  NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(100)  NOT NULL DEFAULT 'system',
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email          ON users (email)          WHERE deleted = FALSE;
CREATE INDEX idx_users_username       ON users (username)       WHERE deleted = FALSE;
CREATE INDEX idx_users_oauth_id       ON users (oauth_provider, oauth_id) WHERE oauth_id IS NOT NULL AND deleted = FALSE;
CREATE INDEX idx_outbox_status        ON outbox_events (status) WHERE deleted = FALSE;
CREATE INDEX idx_outbox_idempotency   ON outbox_events (idempotency_key);

INSERT INTO roles (name, description) VALUES
    ('ADMIN',  'System administrator with full access'),
    ('USER',   'Standard user'),
    ('ARTIST', 'Music producer / artist');
