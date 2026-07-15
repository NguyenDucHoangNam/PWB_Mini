package com.pwb.backend.service;

import java.util.UUID;

public interface AuthEventPublisher {

    String AGGREGATE_USER = "USER";
    String EVENT_REGISTER_OTP = "USER_REGISTERED_OTP";
    String EVENT_LOGIN_SUCCESS = "USER_LOGIN_SUCCESS";
    String EVENT_LOGOUT = "USER_LOGGED_OUT";
    String EVENT_PASSWORD_RESET_REQUESTED = "USER_PASSWORD_RESET_REQUESTED";
    String EVENT_PASSWORD_CHANGED = "USER_PASSWORD_CHANGED";
    String EVENT_USER_REGISTERED_GOOGLE = "USER_REGISTERED_GOOGLE";
    String EVENT_USER_LINKED_GOOGLE = "USER_LINKED_GOOGLE";

    void publishUserRegisteredOtp(UUID userId, String email, String otp);

    void publishLoginSuccess(UUID userId, String email);

    void publishLogout(UUID userId, String email);

    void publishPasswordResetRequested(UUID userId, String email, String resetLink, long ttlMinutes);

    void publishPasswordChanged(UUID userId, String email);

    void publishUserRegisteredGoogle(UUID userId, String email, String fullName);

    void publishUserLinkedGoogle(UUID userId, String email, String fullName);
}