package com.pwb.iam.domain.event;

import java.util.UUID;

public interface AuthEventPublisher {

    void publishAuthSuccess(AuthSuccessEvent event);

    void publishAuthSuccess(UUID userId, String email, String clientIp);

    void publishLoginFailed(String email, String clientIp, String reason);

    void publishLogout(UUID userId, String email, String clientIp);

    void publishOtpIssued(OtpIssuedDomainEvent event);

    void publishOtpVerified(OtpVerifiedDomainEvent event);

    void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes);

    void publishPasswordChanged(UUID userId, String email, String clientIp);

    void publishUserRegisteredGoogle(UUID userId, String email, String fullName);

    void publishUserLinkedGoogle(UUID userId, String email, String fullName);

    void publishGoogleLoginSuccess(UUID userId, String email, String clientIp);
}