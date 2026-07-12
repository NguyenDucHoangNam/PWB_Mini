-- ============================================================================
-- V15: Create audio_processing_jobs table for Secure Audio Streaming (docs 03 §3.2)
-- ============================================================================

CREATE TABLE audio_processing_jobs (
    id                UUID         PRIMARY KEY,
    demo_id           UUID         NOT NULL UNIQUE REFERENCES demos(id),
    status            VARCHAR(20)  NOT NULL,
    attempt_count     INTEGER      NOT NULL DEFAULT 0,
    last_error        TEXT,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64),
    updated_by        VARCHAR(64),
    deleted_at        TIMESTAMPTZ,
    deleted_by        VARCHAR(64),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT apj_status_check CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT apj_attempt_count_check CHECK (attempt_count >= 0 AND attempt_count <= 3)
);

CREATE INDEX idx_apj_status_updated
    ON audio_processing_jobs (status, updated_at);

CREATE INDEX idx_apj_deleted_at
    ON audio_processing_jobs (deleted_at);