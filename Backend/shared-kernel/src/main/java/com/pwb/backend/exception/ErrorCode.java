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
    AUTH_OTP_DAILY_LIMIT_EXCEEDED   ("IAM_027", "You have reached the daily OTP request limit. Please try again tomorrow.", 429),

    UNAUTHORIZED ("SYS_002", "Authentication required.", 401),
    FORBIDDEN    ("SYS_003", "Access denied.", 403),
    RESOURCE_NOT_FOUND ("SYS_004", "Resource not found.", 404),

    ORDER_NOT_FOUND      ("ORD_001", "Order not found.", 404),
    OUT_OF_STOCK         ("INV_001", "Product is out of stock.", 400),

    STORAGE_UPLOAD_FAILED    ("STORAGE_001", "Storage upload failed.",          500),
    STORAGE_DOWNLOAD_FAILED  ("STORAGE_002", "Storage download failed.",        500),
    STORAGE_OBJECT_NOT_FOUND ("STORAGE_003", "Storage object not found.",       404),
    STORAGE_DELETE_FAILED    ("STORAGE_004", "Storage delete failed.",          500),
    STORAGE_PRESIGN_FAILED   ("STORAGE_005", "Storage presign URL failed.",     500),
    STORAGE_INVALID_KEY      ("STORAGE_006", "Invalid storage key format.",     400),

    VOICE_TAG_NOT_FOUND      ("VOICE_001", "Voice tag not found.",                 404),
    SONG_NOT_FOUND           ("VOICE_002", "Song not found.",                      404),
    INVALID_AUDIO_FORMAT     ("VOICE_003", "Invalid audio format.",                400),
    FILE_TOO_LARGE           ("VOICE_004", "File size exceeds maximum allowed.",   413),
    TTS_GENERATION_FAILED    ("VOICE_005", "Text-to-speech generation failed.",    500),
    AUDIO_PROCESSING_FAILED  ("VOICE_006", "Audio processing failed.",             500),
    INVALID_INTERVAL         ("VOICE_007", "Invalid interval value.",              400),
    DUPLICATE_VOICE_TAG_NAME ("VOICE_008", "Voice tag name already exists.",       409),
    SONG_NOT_READY           ("VOICE_009", "Song is not ready for streaming.",     409),
    ACCESS_DENIED_PRO_ONLY   ("VOICE_010", "This feature is available for PRO only.", 403),
    VOICE_TAG_IN_USE         ("VOICE_011", "Voice tag is currently in use.",       409),
    SONG_ALREADY_PROCESSED   ("VOICE_012", "Song has already been processed.",     409),
    INVALID_AUDIO_DURATION   ("VOICE_013", "Audio duration exceeds maximum.",      400),

    LIVEROOM_NOT_FOUND              ("LIVEROOM_001", "Live room not found.",                              404),
    LIVEROOM_CODE_ALREADY_EXISTS    ("LIVEROOM_002", "Room code already exists.",                         409),
    LIVEROOM_HOST_ALREADY_ACTIVE    ("LIVEROOM_003", "Host already has an active room.",                  409),
    LIVEROOM_INVALID_MODE           ("LIVEROOM_004", "Invalid room mode for the given configuration.",   400),
    LIVEROOM_PASSWORD_REQUIRED      ("LIVEROOM_005", "Password is required for password-protected room.", 400),
    LIVEROOM_NOT_HOST               ("LIVEROOM_006", "Only the host can modify this room.",               403),
    LIVEROOM_ALREADY_ENDED          ("LIVEROOM_007", "Room has already ended.",                           409),
    LIVEROOM_CODE_GENERATION_FAILED ("LIVEROOM_008", "Failed to generate unique room code.",              500),
    LIVEROOM_INVALID_CAPACITY       ("LIVEROOM_009", "Max participants must be between 2 and 5.",     400),
    LIVEROOM_MODE_NOT_JOINABLE      ("LIVEROOM_010", "Room mode does not allow public join.",            403),
    LIVEROOM_FULL                   ("LIVEROOM_011", "Room is at maximum capacity.",                     409),
    LIVEROOM_NOT_JOINED             ("LIVEROOM_012", "You have not joined this room.",                   404),
    LIVEROOM_ALREADY_JOINED         ("LIVEROOM_013", "You have already joined this room.",               409),

    LIVEROOM_JOIN_REQUEST_NOT_FOUND      ("LIVEROOM_020", "Join request not found.",                            404),
    LIVEROOM_JOIN_REQUEST_ALREADY_PENDING("LIVEROOM_021", "You already have a pending join request.",          409),
    LIVEROOM_JOIN_REQUEST_NOT_PENDING    ("LIVEROOM_022", "Join request is no longer pending.",                 409),
    LIVEROOM_JOIN_REQUEST_NOT_OWNER      ("LIVEROOM_023", "You can only manage your own join request.",         403),
    LIVEROOM_JOIN_REQUEST_INVALID_DECISION("LIVEROOM_024", "Join request decision is invalid for this room.",   400),
    LIVEROOM_NOT_REQUIRE_APPROVAL       ("LIVEROOM_025", "Join request is not allowed for public rooms.",       400);

    private final String code;
    private final String message;
    private final int httpStatus;
}
