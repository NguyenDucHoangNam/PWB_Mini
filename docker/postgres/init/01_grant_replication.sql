-- =============================================================================
-- Grant REPLICATION privilege to pwb_user for Debezium CDC
-- =============================================================================
-- Debezium requires the connecting user to have BOTH:
--   1. LOGIN (default for CREATE USER)
--   2. REPLICATION (must be granted explicitly)
--
-- Idempotent: safe to re-run.
-- =============================================================================

ALTER USER pwb_user WITH REPLICATION;