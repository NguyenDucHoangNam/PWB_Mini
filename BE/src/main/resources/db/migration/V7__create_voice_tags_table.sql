CREATE TABLE voice_tags (
    id              UUID         PRIMARY KEY,
    owner_id        UUID         NOT NULL REFERENCES users(id),
    text_content    VARCHAR(500) NOT NULL,
    voice_name      VARCHAR(100) NOT NULL,
    language_code   VARCHAR(10)  NOT NULL,
    s3_key          VARCHAR(255) NOT NULL,
    file_size       INTEGER      NOT NULL DEFAULT 0,
    storage_url     VARCHAR(255),
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_voice_tags_owner_active
    ON voice_tags (owner_id) WHERE deleted = FALSE;

CREATE UNIQUE INDEX idx_one_default_per_user
    ON voice_tags (owner_id) WHERE is_default = TRUE AND deleted = FALSE;
