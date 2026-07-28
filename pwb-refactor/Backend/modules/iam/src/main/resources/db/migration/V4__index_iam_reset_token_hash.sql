CREATE INDEX IF NOT EXISTS ix_iam_reset_token_hash
    ON iam_password_reset_tokens(token_hash)
    WHERE deleted = FALSE AND used = FALSE;