-- V7: Add performance indexes for IAM queries

-- Index for OtpCodeJpaEntity.findLatestByUserIdAndPurposeAndStatus
CREATE INDEX ix_otp_codes_user_purpose_status
    ON otp_codes (user_id, purpose, status, expires_at DESC);

-- Index for expired OTP cleanup job
CREATE INDEX ix_otp_codes_expires_at
    ON otp_codes (expires_at)
    WHERE status = 'PENDING';

-- Index for password reset token lookup by user
CREATE INDEX ix_iam_password_reset_tokens_user_expires
    ON iam_password_reset_tokens (user_id, expires_at)
    WHERE used = false;
