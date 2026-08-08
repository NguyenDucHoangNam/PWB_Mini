ALTER TABLE outbox_events
    ADD COLUMN lease_until TIMESTAMPTZ(6) NULL;

CREATE INDEX idx_outbox_lease_until
    ON outbox_events (lease_until)
    WHERE status = 'PROCESSING';
