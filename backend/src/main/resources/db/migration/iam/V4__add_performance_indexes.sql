-- =============================================================================
-- Indexes for IAM module: improved lookups for queries that are common in
-- production but were not yet covered by the original migration.
-- =============================================================================

-- Login lookup by username OR email is one of the hottest queries. The existing
-- partial unique indexes (V2) only cover active rows. Most username/email
-- lookups already filter on deleted=false, so we keep the partial indexes for
-- uniqueness but add a non-partial supporting index to speed up
-- "find by username or email" across the entire table.
CREATE INDEX IF NOT EXISTS idx_users_email_login ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username_login ON users(username);

-- Speed up the account-anonymization cron job that scans by status +
-- deletion_requested_at.
CREATE INDEX IF NOT EXISTS idx_users_pending_deletion
    ON users(status, deletion_requested_at)
    WHERE status = 'PENDING_DELETION';

-- Allow Debezium outbox CDC engine to stream INSERTs cheaply.
CREATE INDEX IF NOT EXISTS idx_outbox_events_status_created
    ON outbox_events(status, created_at);

-- Allow operators to triage the dead-letter queue without a full scan.
CREATE INDEX IF NOT EXISTS idx_outbox_events_dead_lettered_at
    ON outbox_events(dead_lettered_at)
    WHERE status = 'DEAD_LETTERED';