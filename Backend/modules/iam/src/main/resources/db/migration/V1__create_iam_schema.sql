CREATE TABLE iam_roles (
    id              UUID         PRIMARY KEY,
    name            VARCHAR(32)  NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(128) NOT NULL,
    updated_by      VARCHAR(128) NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_roles_name UNIQUE (name)
);

CREATE INDEX ix_iam_roles_name ON iam_roles(name);

CREATE TABLE iam_users (
    id                       UUID         PRIMARY KEY,
    username                 VARCHAR(64)  NOT NULL,
    email                    VARCHAR(255) NOT NULL,
    password                 VARCHAR(255),
    full_name                VARCHAR(128),
    avatar_url               VARCHAR(512),
    phone                    VARCHAR(32),
    status                   VARCHAR(32)  NOT NULL,
    role_id                  UUID         REFERENCES iam_roles(id),
    oauth_provider           VARCHAR(16)  NOT NULL,
    oauth_id                 VARCHAR(255),
    is_provisional_username  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               VARCHAR(128) NOT NULL,
    updated_by               VARCHAR(128) NOT NULL,
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_users_email    UNIQUE (email),
    CONSTRAINT uk_iam_users_username UNIQUE (username),
    CONSTRAINT uk_iam_users_oauth    UNIQUE (oauth_provider, oauth_id)
);

CREATE INDEX ix_iam_users_email    ON iam_users(email);
CREATE INDEX ix_iam_users_oauth    ON iam_users(oauth_provider, oauth_id);
CREATE INDEX ix_iam_users_role_id  ON iam_users(role_id);
CREATE INDEX ix_iam_users_status   ON iam_users(status);

CREATE TABLE iam_otp_codes (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL,
    purpose       VARCHAR(32)  NOT NULL,
    code_hash     VARCHAR(255) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    expires_at    TIMESTAMP    NOT NULL,
    verified_at   TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(128) NOT NULL,
    updated_by    VARCHAR(128) NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_iam_otp_user_purpose ON iam_otp_codes(user_id, purpose, status);
CREATE INDEX ix_iam_otp_expires      ON iam_otp_codes(expires_at);

CREATE TABLE iam_password_reset_tokens (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL,
    token_hash   VARCHAR(255) NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    used         BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at      TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(128) NOT NULL,
    updated_by   VARCHAR(128) NOT NULL,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_iam_reset_user    ON iam_password_reset_tokens(user_id);
CREATE INDEX ix_iam_reset_expires ON iam_password_reset_tokens(expires_at);
