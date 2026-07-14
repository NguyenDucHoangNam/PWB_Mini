package com.pwb.backend.modules.iam.service;

public interface EmailRateLimiter {

    void checkRegisterAttempt(String email);

    void checkResendOtpAttempt(String email);

    void checkForgotPasswordAttempt(String email);

    void clearRegisterAttempts(String email);
}