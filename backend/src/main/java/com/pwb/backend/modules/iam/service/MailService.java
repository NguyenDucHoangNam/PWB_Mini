package com.pwb.backend.modules.iam.service;

public interface MailService {

    void sendOtpEmail(String toEmail, String fullName, String otp);
}