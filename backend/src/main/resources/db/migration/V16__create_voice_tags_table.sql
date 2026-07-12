-- ============================================================================
-- V16: Create voice_tags table for Secure Audio Streaming (docs 03 §3.1)
-- ============================================================================

CREATE TABLE voice_tags (
    id              UUID         PRIMARY KEY,
    owner_id        UUID         NOT NULL REFERENCES users(id),
    text_content    VARCHAR(500) NOT NULL,
    voice_name      VARCHAR(100) NOT NULL,
    language_code   VARCHAR(10)  NOT NULL,
    s3_key          VARCHAR(255) NOT NULL,
    file_size       INTEGER      NOT NULL DEFAULT 0,
    storage_url     VARCHAR(255),
    is_default      BOOLEAN      NOT NULL DEFAULT false,
    is_deleted      BOOLEAN      NOT NULL DEFAULT false,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    deleted_by      VARCHAR(64),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT voice_tags_file_size_check CHECK (file_size >= 0),
    CONSTRAINT voice_tags_language_code_format CHECK (language_code ~ '^[a-z]{2}-[A-Z]{2}$')
);

CREATE INDEX idx_voice_tags_owner_active
    ON voice_tags (owner_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX idx_one_default_per_user
    ON voice_tags (owner_id)
    WHERE is_default = true AND is_deleted = false;

CREATE INDEX idx_voice_tags_deleted_at
    ON voice_tags (deleted_at);
