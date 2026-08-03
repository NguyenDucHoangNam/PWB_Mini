package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.OtpGenerator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class SecureOtpGenerator implements OtpGenerator {

    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean matches(String rawCode, String hashedCode) {
        if (rawCode == null || hashedCode == null) {
            return false;
        }
        String computed = hash(rawCode);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                hashedCode.getBytes(StandardCharsets.UTF_8)
        );
    }
}
