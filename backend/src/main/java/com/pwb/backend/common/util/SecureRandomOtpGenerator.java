package com.pwb.backend.common.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureRandomOtpGenerator {

    private static final int OTP_LENGTH = 6;
    private static final int MAX_VALUE = 1_000_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate() {
        int number = SECURE_RANDOM.nextInt(MAX_VALUE);
        return String.format("%0" + OTP_LENGTH + "d", number);
    }

    public int length() {
        return OTP_LENGTH;
    }
}
