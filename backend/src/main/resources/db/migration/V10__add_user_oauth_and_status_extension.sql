-- ============================================================================
-- V10: Add OAuth provider support and expand user status for IAM login/refresh
-- ============================================================================

ALTER TABLE users
    ADD COLUMN oauth_provider VARCHAR(16),
    ADD COLUMN oauth_id        VARCHAR(255),
    ADD COLUMN avatar_url      VARCHAR(512);

UPDATE users
SET oauth_provider = 'LOCAL'
WHERE oauth_provider IS NULL;

ALTER TABLE users
    ALTER COLUMN oauth_provider SET NOT NULL;

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT users_status_check;

ALTER TABLE users
    ADD CONSTRAINT users_status_check
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BANNED', 'PENDING_DELETION'));

CREATE UNIQUE INDEX idx_users_oauth_provider_oauth_id
    ON users (oauth_provider, oauth_id)
    WHERE oauth_id IS NOT NULL;

CREATE INDEX idx_users_oauth_provider
    ON users (oauth_provider);
