-- V10: Add is_provisional_username flag to iam_users
-- Purpose: distinguish Google-created provisional accounts (auto username)
--          from real user accounts, replacing the fragile startsWith("user_") heuristic.

ALTER TABLE iam_users
    ADD COLUMN is_provisional_username BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE iam_users
SET is_provisional_username = TRUE
WHERE username LIKE 'user_%' AND deleted = FALSE;

CREATE INDEX ix_iam_users_provisional
    ON iam_users (is_provisional_username)
    WHERE is_provisional_username = TRUE;
