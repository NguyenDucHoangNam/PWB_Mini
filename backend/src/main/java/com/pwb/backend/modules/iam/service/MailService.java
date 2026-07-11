package com.pwb.backend.modules.iam.service;

public interface MailService {

    void sendOtpEmail(String toEmail, String fullName, String otp);

    void sendPasswordResetEmail(String toEmail, String fullName, String resetUrl, long ttlMinutes);
}