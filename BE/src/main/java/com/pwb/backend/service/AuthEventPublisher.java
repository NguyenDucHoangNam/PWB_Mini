package com.pwb.backend.service;

import java.util.UUID;

public interface AuthEventPublisher {

    String AGGREGATE_USER = "USER";
    String EVENT_REGISTER_OTP = "USER_REGISTERED_OTP";
    String EVENT_LOGIN_SUCCESS = "USER_LOGIN_SUCCESS";
    String EVENT_LOGOUT = "USER_LOGGED_OUT";

    void publishUserRegisteredOtp(UUID userId, String email, String otp);

    void publishLoginSuccess(UUID userId, String email);

    void publishLogout(UUID userId, String email);
}