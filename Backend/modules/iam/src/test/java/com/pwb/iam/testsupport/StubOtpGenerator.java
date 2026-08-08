package com.pwb.iam.testsupport;

import com.pwb.iam.domain.service.OtpGenerator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StubOtpGenerator implements OtpGenerator {

    private static final String DEFAULT_CODE = "123456";

    private final Map<String, String> hashByCode = new ConcurrentHashMap<>();
    private String nextCode = DEFAULT_CODE;

    public StubOtpGenerator presetNextCode(String code) {
        this.nextCode = code;
        return this;
    }

    public StubOtpGenerator presetHash(String code, String hash) {
        hashByCode.put(code, hash);
        return this;
    }

    @Override
    public String generate(int length) {
        return nextCode;
    }

    @Override
    public String hash(String rawCode) {
        if (hashByCode.containsKey(rawCode)) {
            return hashByCode.get(rawCode);
        }
        return sha256(rawCode);
    }

    @Override
    public boolean matches(String rawCode, String hashedCode) {
        if (rawCode == null || hashedCode == null) {
            return false;
        }
        String expected = hash(rawCode);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                hashedCode.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
