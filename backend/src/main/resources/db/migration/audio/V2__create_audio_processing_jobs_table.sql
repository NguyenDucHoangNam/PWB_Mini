CREATE TABLE audio_processing_jobs (
    id VARCHAR(36) PRIMARY KEY,
    demo_id VARCHAR(36) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    CONSTRAINT chk_apj_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_apj_attempt CHECK (attempt_count >= 0 AND attempt_count <= 5),
    CONSTRAINT fk_apj_demo_id FOREIGN KEY (demo_id) REFERENCES demos(id) ON DELETE CASCADE
);

CREATE INDEX idx_apj_status_updated ON audio_processing_jobs(status, updated_at) WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_apj_demo ON audio_processing_jobs(demo_id);
