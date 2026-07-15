package com.pwb.backend.service;

import java.util.UUID;

public interface NotificationService {

    void sendOtpEmail(UUID userId, String email, String otp);
}
