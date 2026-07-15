package com.pwb.backend.service;

import java.util.UUID;

public interface NotificationService {

    void sendOtpEmail(UUID userId, String email, String otp);

    void sendPasswordResetLinkEmail(UUID userId, String email, String resetLink, long ttlMinutes);

    void sendPasswordChangedEmail(UUID userId, String email);

    void sendWelcomeGoogleEmail(UUID userId, String email, String fullName);
}