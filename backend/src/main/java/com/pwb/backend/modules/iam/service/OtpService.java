package com.pwb.backend.modules.iam.service;

public interface OtpService {

    void issueOtp(String email, String otp);

    boolean verifyOtp(String email, String otp);

    boolean isLocked(String email);

    boolean canResend(String email);

    void markResent(String email);

    long lockoutRetryAfterSeconds();
}