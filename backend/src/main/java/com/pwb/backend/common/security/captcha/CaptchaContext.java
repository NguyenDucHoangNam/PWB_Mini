package com.pwb.backend.common.security.captcha;

public record CaptchaContext(
        CaptchaScope scope,
        String identifier) {

    public static CaptchaContext register(String email) {
        return new CaptchaContext(CaptchaScope.REGISTER, normalize(email));
    }

    public static CaptchaContext login(String usernameOrEmail) {
        return new CaptchaContext(CaptchaScope.LOGIN, normalize(usernameOrEmail));
    }

    public static CaptchaContext googleLogin(String email) {
        return new CaptchaContext(CaptchaScope.GOOGLE_LOGIN, normalize(email));
    }

    public static CaptchaContext resendOtp(String email) {
        return new CaptchaContext(CaptchaScope.RESEND_OTP, normalize(email));
    }

    public static CaptchaContext verifyOtp(String email) {
        return new CaptchaContext(CaptchaScope.VERIFY_OTP, normalize(email));
    }

    public static CaptchaContext forgotPassword(String email) {
        return new CaptchaContext(CaptchaScope.FORGOT_PASSWORD, normalize(email));
    }

    public static CaptchaContext resetPassword() {
        return new CaptchaContext(CaptchaScope.RESET_PASSWORD, "anonymous");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "anonymous";
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? "anonymous" : trimmed;
    }
}
