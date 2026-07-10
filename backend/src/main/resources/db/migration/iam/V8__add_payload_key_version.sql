ALTER TABLE outbox_events ADD COLUMN payload_key_version INTEGER;
CREATE INDEX idx_outbox_key_version ON outbox_events(payload_key_version);