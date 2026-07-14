-- ============================================================================
-- V21: Add PRO role for premium users (seeding V21+).
-- ============================================================================

INSERT INTO roles (id, code, name, created_by, updated_by)
VALUES ('33333333-3333-3333-3333-333333333333', 'PRO', 'Pro user', 'SYSTEM', 'SYSTEM')
ON CONFLICT (code) DO NOTHING;