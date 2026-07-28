package com.pwb.iam.domain.event;

import java.util.UUID;

public interface AuthEventPublisher {

    void publishUserRegisteredOtp(UUID userId, String email, String otp);

    void publishLoginSuccess(UUID userId, String email);

    void publishLogout(UUID userId, String email);

    void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes);

    void publishPasswordChanged(UUID userId, String email);

    void publishUserRegisteredGoogle(UUID userId, String email, String fullName);

    void publishUserLinkedGoogle(UUID userId, String email, String fullName);

    void publishUserVerifiedEmail(UUID userId, String email);
}
