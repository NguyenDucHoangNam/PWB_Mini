package com.pwb.iam.domain.event;

import java.util.UUID;

public interface AuthEventPublisher {

    void publishAuthSuccess(AuthSuccessEvent event);

    void publishOtpIssued(OtpIssuedDomainEvent event);

    void publishOtpVerified(OtpVerifiedDomainEvent event);

    void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes);

    void publishPasswordChanged(UUID userId, String email);

    void publishUserRegisteredGoogle(UUID userId, String email, String fullName);

    void publishUserLinkedGoogle(UUID userId, String email, String fullName);
}