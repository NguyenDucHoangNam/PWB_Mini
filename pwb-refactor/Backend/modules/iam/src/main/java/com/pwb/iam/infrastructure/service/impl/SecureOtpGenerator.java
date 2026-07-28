package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.OtpGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureOtpGenerator implements OtpGenerator {

    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public SecureOtpGenerator(org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String generate() {
        return generate(6);
    }

    public String generate(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(DIGITS[RANDOM.nextInt(DIGITS.length)]);
        }
        return builder.toString();
    }

    @Override
    public String hash(String rawCode) {
        return passwordEncoder.encode(rawCode);
    }

    @Override
    public boolean matches(String rawCode, String hashedCode) {
        return rawCode != null && hashedCode != null && passwordEncoder.matches(rawCode, hashedCode);
    }
}
