-- ============================================================================
-- V18: Extend outbox_events columns for Distribute & Share module (docs 03 §C)
--      - idempotency_key    : UUID UNIQUE for consumer dedup
--      - payload_key_version: AES key version used (for rotation)
--      - available_at       : alias of next_attempt_at (scheduler uses both)
-- ============================================================================

ALTER TABLE outbox_events
    ADD COLUMN idempotency_key     UUID,
    ADD COLUMN payload_key_version INT NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX idx_outbox_idempotency
    ON outbox_events (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_outbox_status_available
    ON outbox_events (status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');