package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.ErrorCategory;
import com.pwb.shared.exception.ErrorCode;

public enum IamErrorCode implements ErrorCode {

    EMAIL_ALREADY_REGISTERED          (ErrorCategory.CONFLICT,         "IAM_001", "Email already registered."),
    WEAK_PASSWORD                     (ErrorCategory.VALIDATION,       "IAM_002", "Password is too weak."),
    USER_NOT_FOUND                    (ErrorCategory.NOT_FOUND,        "IAM_003", "User not found."),
    LOGIN_BAD_CREDENTIALS             (ErrorCategory.UNAUTHORIZED,     "IAM_004", "Invalid email or password."),
    ACCOUNT_LOCKED                    (ErrorCategory.TOO_MANY_REQUESTS,"IAM_005", "Account is temporarily locked due to too many failed login attempts."),
    ACCOUNT_INACTIVE                  (ErrorCategory.FORBIDDEN,        "IAM_006", "Account is inactive."),
    ACCOUNT_NOT_VERIFIED              (ErrorCategory.FORBIDDEN,        "IAM_007", "Account is not verified yet."),
    AUTH_TOKEN_INVALID                (ErrorCategory.UNAUTHORIZED,     "IAM_008", "Authentication token is invalid."),
    AUTH_TOKEN_EXPIRED                (ErrorCategory.UNAUTHORIZED,     "IAM_009", "Authentication token has expired."),
    AUTH_TOKEN_MISSING                (ErrorCategory.UNAUTHORIZED,     "IAM_010", "Authentication token is required."),
    REFRESH_TOKEN_INVALID             (ErrorCategory.UNAUTHORIZED,     "IAM_011", "Refresh token is invalid."),
    REFRESH_TOKEN_EXPIRED             (ErrorCategory.UNAUTHORIZED,     "IAM_012", "Refresh token has expired."),
    REFRESH_TOKEN_REUSED              (ErrorCategory.UNAUTHORIZED,     "IAM_013", "Refresh token has been revoked."),
    RATE_LIMITED                      (ErrorCategory.TOO_MANY_REQUESTS,"IAM_014", "Too many requests. Please slow down."),
    AUTH_INVALID_CURRENT_PASSWORD     (ErrorCategory.VALIDATION,       "IAM_010", "Current password is incorrect."),
    AUTH_PASSWORD_REUSED              (ErrorCategory.VALIDATION,       "IAM_011", "New password must be different from the current one."),
    USERNAME_ALREADY_TAKEN            (ErrorCategory.CONFLICT,         "IAM_013", "Username is already taken."),
    AUTH_RESET_TOKEN_INVALID          (ErrorCategory.VALIDATION,       "IAM_014", "Password reset token is invalid or expired."),
    AUTH_OAUTH_USER_NO_PASSWORD       (ErrorCategory.VALIDATION,       "IAM_015", "OAuth account does not have a password."),
    PASSWORD_RESET_COOLDOWN           (ErrorCategory.TOO_MANY_REQUESTS,"IAM_017", "Please wait before requesting another password reset."),
    ROLE_NOT_FOUND                    (ErrorCategory.INTERNAL,         "IAM_018", "Default role does not exist in database."),
    AUTH_OTP_INVALID                  (ErrorCategory.VALIDATION,       "IAM_021", "Invalid OTP code."),
    AUTH_OTP_EXPIRED                  (ErrorCategory.VALIDATION,       "IAM_022", "OTP code has expired or does not exist."),
    AUTH_RATE_LIMIT_EXCEEDED          (ErrorCategory.TOO_MANY_REQUESTS,"IAM_026", "Too many requests. Please try again later."),
    AUTH_OTP_DAILY_LIMIT_EXCEEDED     (ErrorCategory.TOO_MANY_REQUESTS,"IAM_027", "You have reached the daily OTP request limit. Please try again tomorrow."),
    AUTH_GOOGLE_TOKEN_INVALID         (ErrorCategory.UNAUTHORIZED,     "IAM_GOOGLE_001", "Google ID token is invalid or expired."),
    AUTH_GOOGLE_EMAIL_NOT_VERIFIED    (ErrorCategory.UNAUTHORIZED,     "IAM_GOOGLE_002", "Google email is not verified."),
    ACCESS_DENIED                     (ErrorCategory.FORBIDDEN,        "IAM_ACCESS_001", "Access denied due to insufficient permissions.");

    IamErrorCode(ErrorCategory category, String code, String defaultMessage) {
        this.category = category;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    private final ErrorCategory category;
    private final String code;
    private final String defaultMessage;

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}