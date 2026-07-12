-- ============================================================================
-- V14: Create demos table for Secure Audio Streaming (docs 03 §3.1)
-- ============================================================================

CREATE TABLE demos (
    id                     UUID         PRIMARY KEY,
    title                  VARCHAR(100) NOT NULL,
    owner_id               UUID         NOT NULL REFERENCES users(id),
    original_s3_key        VARCHAR(255) NOT NULL,
    file_size              BIGINT       NOT NULL,
    hls_playlist_s3_key    VARCHAR(255),
    voice_tag_id           UUID,
    status                 VARCHAR(20)  NOT NULL,
    duration               NUMERIC(10, 2),
    sample_rate            INTEGER,
    format                 VARCHAR(10),
    waveform_data          JSONB,
    aes_key_encrypted      BYTEA,
    error_message          TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(64),
    updated_by             VARCHAR(64),
    deleted_at             TIMESTAMPTZ,
    deleted_by             VARCHAR(64),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT demos_status_check CHECK (status IN ('PROCESSING', 'ACTIVE', 'FAILED', 'DELETED'))
);

CREATE INDEX idx_demos_owner_created
    ON demos (owner_id, created_at DESC);

CREATE INDEX idx_demos_status_updated
    ON demos (status, updated_at)
    WHERE status IN ('PROCESSING', 'FAILED');

CREATE INDEX idx_demos_owner_status_active
    ON demos (owner_id, status)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_demos_deleted_at
    ON demos (deleted_at);