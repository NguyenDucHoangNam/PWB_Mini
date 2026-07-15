ALTER TABLE outbox_events
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_error VARCHAR(500),
    ADD COLUMN next_retry_at TIMESTAMPTZ;

UPDATE outbox_events SET status = 'PUBLISHED' WHERE status = 'PROCESSED';

CREATE INDEX idx_outbox_pending_retry ON outbox_events (status, next_retry_at)
    WHERE status = 'PENDING';
