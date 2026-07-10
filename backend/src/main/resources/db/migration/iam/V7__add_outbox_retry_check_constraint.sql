ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_retry CHECK (retry_count >= 0 AND retry_count <= 5);