package com.pwb.iam.domain.event;

import java.util.UUID;

public interface AuthEventPublisher {

    void publishAuthSuccess(AuthSuccessEvent event);

    void publishAuthSuccess(UUID userId, String email, String clientIp, String userAgent);

    void publishLoginFailed(String email, String clientIp, String userAgent, String reason);

    void publishLogout(UUID userId, String email, String clientIp, String userAgent);

    void publishLogout(UUID userId, String clientIp, String userAgent);

    void publishOtpIssued(OtpIssuedDomainEvent event);

    void publishOtpVerified(OtpVerifiedDomainEvent event);

    void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes, String userAgent);

    void publishPasswordChanged(UUID userId, String email, String clientIp, String userAgent);

    void publishUserRegisteredGoogle(UUID userId, String email, String fullName);

    void publishUserLinkedGoogle(UUID userId, String email, String fullName);

    void publishGoogleLoginSuccess(UUID userId, String email, String clientIp, String userAgent);

    void publishGoogleLoginFailed(String email, String clientIp, String userAgent, String reason);
}
