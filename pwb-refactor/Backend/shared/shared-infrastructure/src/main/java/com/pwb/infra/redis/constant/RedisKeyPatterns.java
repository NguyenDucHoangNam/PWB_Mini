package com.pwb.infra.redis.constant;

public final class RedisKeyPatterns {

    private RedisKeyPatterns() {
    }

    public static final String LOGIN_FAIL_EMAIL = "login:fail:email:";
    public static final String LOGIN_FAIL_IP = "login:fail:ip:";
    public static final String LOGIN_LOCK_EMAIL = "login:lock:email:";
    public static final String LOGIN_LOCK_IP = "login:lock:ip:";

    public static final String RATELIMIT_PREFIX = "ratelimit:";

    public static final String REFRESH_TOKEN = "refresh:token:";
    public static final String REFRESH_USER = "refresh:user:";

    public static final String OTP_PREFIX = "otp:";
    public static final String OTP_LAST_SENT = "otp:last-sent:";
    public static final String OTP_DAILY_COUNT = "otp:daily-count:";

    public static final String PASSWORD_RESET_COOLDOWN = "password-reset:cooldown:";
}
