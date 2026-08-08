package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.infrastructure.crypto.Hashes;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureOtpGenerator implements OtpGenerator {

    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(DIGITS[RANDOM.nextInt(DIGITS.length)]);
        }
        return builder.toString();
    }

    @Override
    public String hash(String rawCode) {
        return Hashes.sha256Hex(rawCode);
    }

    /**
     * Constant-time so response timing does not leak how much of a guessed code was correct.
     */
    @Override
    public boolean matches(String rawCode, String hashedCode) {
        return Hashes.matches(rawCode, hashedCode);
    }
}
