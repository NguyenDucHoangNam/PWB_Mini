ALTER TABLE outbox_events ADD COLUMN processing_started_at TIMESTAMP WITH TIME ZONE;

DROP INDEX IF EXISTS idx_outbox_pending;
CREATE INDEX idx_outbox_pending
    ON outbox_events (status, available_at, created_at, id)
    WHERE deleted = false;

CREATE INDEX idx_outbox_in_flight_recovery
    ON outbox_events (status, processing_started_at)
    WHERE deleted = false AND status = 'IN_FLIGHT';