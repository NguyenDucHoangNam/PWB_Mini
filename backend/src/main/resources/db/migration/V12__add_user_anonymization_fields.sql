-- ============================================================================
-- V12: Add username + deleted columns, extend users_status_check to allow DELETED
-- ============================================================================

ALTER TABLE users
    ADD COLUMN username VARCHAR(100),
    ADD COLUMN deleted  BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE users DROP CONSTRAINT users_status_check;

ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BANNED', 'PENDING_DELETION', 'DELETED'));

CREATE UNIQUE INDEX idx_users_username_unique
    ON users (username)
    WHERE username IS NOT NULL;

CREATE INDEX idx_users_status_pending_deletion
    ON users (status, deletion_requested_at)
    WHERE status = 'PENDING_DELETION';