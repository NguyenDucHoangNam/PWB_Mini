package com.pwb.backend.common.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class SecureRandomOtpGenerator {

    private static final int OTP_LENGTH = 6;
    private static final int MAX_VALUE = 1_000_000;

    public String generate() {
        int number = ThreadLocalRandom.current().nextInt(MAX_VALUE);
        return String.format("%0" + OTP_LENGTH + "d", number);
    }

    public int length() {
        return OTP_LENGTH;
    }
}