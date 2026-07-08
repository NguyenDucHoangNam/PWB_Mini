-- =============================================================================
-- Additional indexes for FK columns and lookup paths that were missed by V4.
-- These cover (M55-M57): users.role_id, outbox_events.aggregate_id,
-- users.phone for "forgot password by phone" type queries.
-- =============================================================================

-- Speeds up JOIN from users -> roles (e.g. @EntityGraph role fetch).
CREATE INDEX IF NOT EXISTS idx_users_role_id ON users(role_id);

-- Speeds up CDC scanning and operator queries that look up outbox events
-- by aggregate id (e.g. all events for a specific user).
CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate_id ON outbox_events(aggregate_id);

-- Allows the (optional) "find user by phone" lookup used by account recovery.
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone)
    WHERE phone IS NOT NULL AND phone <> '';