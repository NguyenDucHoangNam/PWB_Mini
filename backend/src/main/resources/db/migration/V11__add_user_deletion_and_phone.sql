-- ============================================================================
-- V11: Add phone and deletion_requested_at to users table
-- ============================================================================

ALTER TABLE users
    ADD COLUMN phone                  VARCHAR(20),
    ADD COLUMN deletion_requested_at  TIMESTAMPTZ;

CREATE INDEX idx_users_deletion_status
    ON users (status, deletion_requested_at)
    WHERE status = 'PENDING_DELETION';
