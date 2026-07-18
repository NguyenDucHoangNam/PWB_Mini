CREATE TABLE otp_codes (
    id             BINARY(16)    NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    created_by     VARCHAR(255)  NOT NULL,
    updated_by     VARCHAR(255)  NOT NULL,
    deleted        TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at     DATETIME(6)   NULL,
    version        BIGINT        NOT NULL DEFAULT 0,

    user_id        BINARY(16)    NOT NULL,
    purpose        VARCHAR(32)   NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    expires_at     DATETIME(6)   NOT NULL,
    verified_at    DATETIME(6)   NULL,
    locked_at      DATETIME(6)   NULL,

    PRIMARY KEY (id),
    INDEX ix_otp_codes_user_purpose_status (user_id, purpose, status),
    INDEX ix_otp_codes_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
