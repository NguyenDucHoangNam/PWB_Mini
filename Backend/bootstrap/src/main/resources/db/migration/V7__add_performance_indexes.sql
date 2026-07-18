-- V7: Add performance indexes for IAM queries

-- Index for OtpCodeJpaEntity.findLatestByUserIdAndPurposeAndStatus
DROP INDEX IF EXISTS ix_otp_codes_user_purpose_status;
CREATE INDEX ix_otp_codes_user_purpose_status
    ON otp_codes (user_id, purpose, status, expires_at DESC);

-- Index for expired OTP cleanup job
DROP INDEX IF EXISTS ix_otp_codes_expires_at;
CREATE INDEX ix_otp_codes_expires_at
    ON otp_codes (expires_at)
    WHERE status = 'PENDING';

-- Index for password reset token lookup by user
DROP INDEX IF EXISTS ix_iam_password_reset_tokens_user_expires;
CREATE INDEX ix_iam_password_reset_tokens_user_expires
    ON iam_password_reset_tokens (user_id, expires_at)
    WHERE used = false;
