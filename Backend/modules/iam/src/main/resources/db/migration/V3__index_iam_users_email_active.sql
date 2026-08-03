CREATE INDEX IF NOT EXISTS ix_iam_users_email_active
    ON iam_users(email)
    WHERE deleted = FALSE;