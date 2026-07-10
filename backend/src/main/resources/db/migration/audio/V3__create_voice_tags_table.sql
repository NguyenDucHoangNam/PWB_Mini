CREATE TABLE voice_tags (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    text_content VARCHAR(500) NOT NULL,
    voice_name VARCHAR(100) NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    s3_key VARCHAR(255) NOT NULL,
    file_size INT NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_voice_tags_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX idx_voice_tags_owner_active
    ON voice_tags (owner_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX idx_one_default_per_user
    ON voice_tags (owner_id)
    WHERE is_default = true AND is_deleted = false;
