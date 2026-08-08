package com.pwb.iam.testsupport;

import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class StubAuthEventPublisher implements AuthEventPublisher {

    public record LoginFailedEvent(String email, String clientIp, String userAgent, String reason) {
    }

    public record LogoutEvent(UUID userId, String clientIp, String userAgent) {
    }

    private final List<AuthSuccessEvent> authSuccessEvents = new ArrayList<>();
    private final List<LoginFailedEvent> loginFailedEvents = new ArrayList<>();
    private final List<LogoutEvent> logoutEvents = new ArrayList<>();
    private final List<OtpIssuedDomainEvent> otpIssuedEvents = new ArrayList<>();
    private final List<OtpVerifiedDomainEvent> otpVerifiedEvents = new ArrayList<>();
    private final List<UUID> passwordChangedEvents = new ArrayList<>();
    private final List<UUID> passwordResetRequestedEvents = new ArrayList<>();
    private final List<UUID> userRegisteredGoogleEvents = new ArrayList<>();
    private final List<UUID> userLinkedGoogleEvents = new ArrayList<>();
    private final List<UUID> googleLoginSuccessEvents = new ArrayList<>();
    private final List<LoginFailedEvent> googleLoginFailedEvents = new ArrayList<>();

    @Override
    public void publishAuthSuccess(AuthSuccessEvent event) {
        authSuccessEvents.add(event);
    }

    @Override
    public void publishLoginFailed(String email, String clientIp, String userAgent, String reason) {
        loginFailedEvents.add(new LoginFailedEvent(email, clientIp, userAgent, reason));
    }

    @Override
    public void publishLogout(UUID userId, String clientIp, String userAgent) {
        logoutEvents.add(new LogoutEvent(userId, clientIp, userAgent));
    }

    @Override
    public void publishOtpIssued(OtpIssuedDomainEvent event) {
        otpIssuedEvents.add(event);
    }

    @Override
    public void publishOtpVerified(OtpVerifiedDomainEvent event) {
        otpVerifiedEvents.add(event);
    }

    @Override
    public void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes, String userAgent) {
        passwordResetRequestedEvents.add(userId);
    }

    @Override
    public void publishPasswordChanged(UUID userId, String email, String clientIp, String userAgent) {
        passwordChangedEvents.add(userId);
    }

    @Override
    public void publishUserRegisteredGoogle(UUID userId, String email, String fullName) {
        userRegisteredGoogleEvents.add(userId);
    }

    @Override
    public void publishUserLinkedGoogle(UUID userId, String email, String fullName) {
        userLinkedGoogleEvents.add(userId);
    }

    @Override
    public void publishGoogleLoginSuccess(UUID userId, String email, String clientIp, String userAgent) {
        googleLoginSuccessEvents.add(userId);
    }

    @Override
    public void publishGoogleLoginFailed(String email, String clientIp, String userAgent, String reason) {
        googleLoginFailedEvents.add(new LoginFailedEvent(email, clientIp, userAgent, reason));
    }

    public List<AuthSuccessEvent> authSuccessEvents() {
        return Collections.unmodifiableList(authSuccessEvents);
    }

    public List<LoginFailedEvent> loginFailedEvents() {
        return Collections.unmodifiableList(loginFailedEvents);
    }

    public List<LogoutEvent> logoutEvents() {
        return Collections.unmodifiableList(logoutEvents);
    }

    public List<OtpIssuedDomainEvent> otpIssuedEvents() {
        return Collections.unmodifiableList(otpIssuedEvents);
    }

    public List<OtpVerifiedDomainEvent> otpVerifiedEvents() {
        return Collections.unmodifiableList(otpVerifiedEvents);
    }

    public List<UUID> passwordChangedEvents() {
        return Collections.unmodifiableList(passwordChangedEvents);
    }

    public List<UUID> passwordResetRequestedEvents() {
        return Collections.unmodifiableList(passwordResetRequestedEvents);
    }

    public List<UUID> userRegisteredGoogleEvents() {
        return Collections.unmodifiableList(userRegisteredGoogleEvents);
    }

    public List<UUID> userLinkedGoogleEvents() {
        return Collections.unmodifiableList(userLinkedGoogleEvents);
    }

    public List<UUID> googleLoginSuccessEvents() {
        return Collections.unmodifiableList(googleLoginSuccessEvents);
    }

    public List<LoginFailedEvent> googleLoginFailedEvents() {
        return Collections.unmodifiableList(googleLoginFailedEvents);
    }

    public void clear() {
        authSuccessEvents.clear();
        loginFailedEvents.clear();
        logoutEvents.clear();
        otpIssuedEvents.clear();
        otpVerifiedEvents.clear();
        passwordChangedEvents.clear();
        passwordResetRequestedEvents.clear();
        userRegisteredGoogleEvents.clear();
        userLinkedGoogleEvents.clear();
        googleLoginSuccessEvents.clear();
        googleLoginFailedEvents.clear();
    }
}
