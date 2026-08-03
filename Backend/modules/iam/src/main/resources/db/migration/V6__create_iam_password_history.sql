CREATE TABLE iam_password_history (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(128) NOT NULL,
    updated_by     VARCHAR(128) NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_password_history_user_created ON iam_password_history(user_id, created_at DESC);
