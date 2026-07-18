CREATE TABLE iam_password_reset_tokens (
    id           UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at   TIMESTAMPTZ(6)  NOT NULL,
    updated_at   TIMESTAMPTZ(6)  NOT NULL,
    created_by   VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by   VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ(6)  NULL,
    version      BIGINT          NOT NULL DEFAULT 0,

    user_id      UUID            NOT NULL,
    token_hash   VARCHAR(64)     NOT NULL,
    expires_at   TIMESTAMPTZ(6)  NOT NULL,
    used         BOOLEAN         NOT NULL DEFAULT FALSE,
    used_at      TIMESTAMPTZ(6)  NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_iam_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_iam_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES iam_users(id)
);

CREATE INDEX ix_iam_password_reset_tokens_user_id ON iam_password_reset_tokens (user_id);
CREATE INDEX ix_iam_password_reset_tokens_expires_at ON iam_password_reset_tokens (expires_at);

DROP TABLE IF EXISTS password_reset_tokens;
