package com.pwb.iam.testsupport;

import com.pwb.iam.domain.service.PasswordResetTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

public final class StubPasswordResetTokenService implements PasswordResetTokenService {

    private static final String SEPARATOR = ".";
    private static final String SECRET = "test-secret-must-be-long-enough-for-hmac-sha256";

    private String lastGenerated;
    private String lastRawToken;

    @Override
    public String generateSignedToken() {
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String signature = hmac(raw);
        String signed = raw + SEPARATOR + signature;
        this.lastGenerated = signed;
        this.lastRawToken = raw;
        return signed;
    }

    @Override
    public String extractRawToken(String signedToken) {
        if (signedToken == null || !signedToken.contains(SEPARATOR)) {
            return null;
        }
        return signedToken.substring(0, signedToken.lastIndexOf(SEPARATOR));
    }

    @Override
    public boolean verifySignature(String signedToken) {
        if (signedToken == null || !signedToken.contains(SEPARATOR)) {
            return false;
        }
        int lastDot = signedToken.lastIndexOf(SEPARATOR);
        String raw = signedToken.substring(0, lastDot);
        String provided = signedToken.substring(lastDot + 1);
        String expected = hmac(raw);
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String hashForStorage(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    @Override
    public String buildResetLink(String rawToken) {
        return "http://localhost:3000/reset-password?token=" + rawToken;
    }

    public String lastGenerated() {
        return lastGenerated;
    }

    public String lastRawToken() {
        return lastRawToken;
    }

    private String hmac(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC failed", ex);
        }
    }
}
