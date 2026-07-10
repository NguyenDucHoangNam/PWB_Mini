CREATE TABLE demos (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(100) NOT NULL,
    original_s3_key VARCHAR(255) NOT NULL,
    confirmed_s3_key VARCHAR(255),
    file_size BIGINT NOT NULL,
    voice_tag_id VARCHAR(36),
    watermark_interval INTEGER NOT NULL DEFAULT 25,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    hls_playlist_s3_key VARCHAR(255),
    duration NUMERIC(10, 2),
    sample_rate INTEGER,
    format VARCHAR(10),
    waveform_data JSONB,
    aes_key_encrypted BYTEA,
    aes_key_version INTEGER,
    previous_aes_key_encrypted BYTEA,
    error_message TEXT,
    stream_s3_cleanup_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    CONSTRAINT chk_demos_status CHECK (status IN ('PROCESSING', 'ACTIVE', 'FAILED', 'DELETED')),
    CONSTRAINT chk_demos_file_size CHECK (file_size >= 1 AND file_size <= 524288000),
    CONSTRAINT chk_demos_watermark_interval CHECK (watermark_interval >= 10 AND watermark_interval <= 60)
);

CREATE INDEX idx_demos_owner_created ON demos(owner_id, created_at DESC);
CREATE INDEX idx_demos_status_updated ON demos(status, updated_at) WHERE status IN ('PROCESSING', 'FAILED');
CREATE INDEX idx_demos_owner_status_active ON demos(owner_id, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_demos_deleted_at ON demos(deleted_at) WHERE status = 'DELETED';
