package com.pwb.backend.modules.iam.service;

import java.time.Instant;

public interface MailService {

    void sendOtpEmail(String toEmail, String fullName, String otp);

    void sendPasswordResetEmail(String toEmail, String fullName, String resetUrl, long ttlMinutes);

    void sendAccountDeletionRequestedEmail(String toEmail,
                                           String fullName,
                                           Instant deletionRequestedAt,
                                           Instant scheduledPermanentDeletionAt,
                                           int graceDays,
                                           String loginUrl);
}