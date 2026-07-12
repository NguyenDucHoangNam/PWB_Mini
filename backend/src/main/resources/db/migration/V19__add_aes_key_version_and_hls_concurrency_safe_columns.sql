-- ============================================================================
-- V19: AES key rotation lifecycle (docs 04 §1.2.E)
-- ============================================================================

ALTER TABLE demos
    ADD COLUMN aes_key_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN previous_aes_key_encrypted BYTEA,
    ADD COLUMN last_rotated_at TIMESTAMPTZ;

-- Partial index to find demos that still hold a previous key (for cleanup job)
CREATE INDEX idx_demos_last_rotated_at
    ON demos (last_rotated_at)
    WHERE previous_aes_key_encrypted IS NOT NULL;