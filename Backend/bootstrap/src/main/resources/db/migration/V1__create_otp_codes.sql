CREATE TABLE otp_codes (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ(6)  NOT NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL,
    created_by      VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ(6)  NULL,
    version         BIGINT          NOT NULL DEFAULT 0,

    user_id         UUID            NOT NULL,
    purpose         VARCHAR(32)     NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    attempts        INT             NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ(6)  NOT NULL,
    verified_at     TIMESTAMPTZ(6)  NULL,
    locked_at       TIMESTAMPTZ(6)  NULL,

    PRIMARY KEY (id)
);

CREATE INDEX ix_otp_codes_user_purpose_status ON otp_codes (user_id, purpose, status);
CREATE INDEX ix_otp_codes_expires_at ON otp_codes (expires_at);
