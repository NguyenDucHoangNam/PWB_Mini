package com.pwb.backend.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR("SYS_000", "Internal server error.", 500),
    INVALID_INPUT        ("SYS_001", "Invalid input.", 400),

    EMAIL_ALREADY_EXISTS ("IAM_001", "Email already registered.", 409),
    WEAK_PASSWORD        ("IAM_002", "Password is too weak.", 400),
    USER_NOT_FOUND       ("IAM_003", "User not found.", 404),
    AUTH_GOOGLE_TOKEN_INVALID       ("IAM_004", "Google ID token is invalid or expired.", 401),
    AUTH_GOOGLE_EMAIL_NOT_VERIFIED  ("IAM_005", "Google email is not verified.", 401),
    AUTH_INVALID_CURRENT_PASSWORD  ("IAM_010", "Current password is incorrect.", 400),
    AUTH_PASSWORD_REUSED           ("IAM_011", "New password must be different from the current one.", 400),
    AUTH_ACCOUNT_NOT_VERIFIED      ("IAM_012", "Account is not verified.", 403),
    AUTH_LOGIN_FAILED              ("IAM_013", "Email or password is incorrect.", 401),
    AUTH_RESET_TOKEN_INVALID       ("IAM_014", "Password reset token is invalid or expired.", 400),
    AUTH_OAUTH_USER_NO_PASSWORD    ("IAM_015", "OAuth account does not have a password.", 400),
    AUTH_TOKEN_INVALID             ("IAM_016", "Token is invalid.", 401),
    PASSWORD_RESET_COOLDOWN        ("IAM_017", "Please wait before requesting another password reset.", 429),
    SEEDER_ROLE_NOT_FOUND          ("IAM_018", "Default role does not exist in database.", 500),
    USER_NAME_EXISTS               ("IAM_019", "Username already exists.", 409),
    EMAIL_ALREADY_REGISTERED_AUTH  ("IAM_020", "Email already registered.", 409),
    AUTH_OTP_INVALID               ("IAM_021", "Invalid OTP code.", 400),
    AUTH_OTP_EXPIRED               ("IAM_022", "OTP code has expired or does not exist.", 400),
    AUTH_OTP_LOCKED                ("IAM_023", "Account temporarily locked due to too many invalid OTP attempts.", 429),
    AUTH_ACCOUNT_LOCKED            ("IAM_024", "Account temporarily locked due to too many failed login attempts.", 429),
    AUTH_IP_LOCKED                 ("IAM_025", "IP temporarily blocked due to too many failed login attempts.", 429),
    AUTH_RATE_LIMIT_EXCEEDED       ("IAM_026", "Too many requests. Please try again in {0} seconds.", 429),

    UNAUTHORIZED ("SYS_002", "Authentication required.", 401),
    FORBIDDEN    ("SYS_003", "Access denied.", 403),

    ORDER_NOT_FOUND      ("ORD_001", "Order not found.", 404),
    OUT_OF_STOCK         ("INV_001", "Product is out of stock.", 400);

    private final String code;
    private final String message;
    private final int httpStatus;
}
