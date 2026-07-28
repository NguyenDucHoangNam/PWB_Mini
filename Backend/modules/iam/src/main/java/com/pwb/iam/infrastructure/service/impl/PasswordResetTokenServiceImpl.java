package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.infrastructure.security.config.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetTokenServiceImpl implements PasswordResetTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTES = 32;
    private static final String TOKEN_SEPARATOR = ".";

    private final SecureRandom secureRandom = new SecureRandom();
    private final PasswordResetProperties passwordResetProperties;

    @Override
    public String generateSignedToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String signature = computeHmac(rawToken);
        return rawToken + TOKEN_SEPARATOR + signature;
    }

    @Override
    public String extractRawToken(String signedToken) {
        if (signedToken == null || !signedToken.contains(TOKEN_SEPARATOR)) {
            return null;
        }
        return signedToken.substring(0, signedToken.lastIndexOf(TOKEN_SEPARATOR));
    }

    @Override
    public boolean verifySignature(String signedToken) {
        if (signedToken == null || !signedToken.contains(TOKEN_SEPARATOR)) {
            return false;
        }
        int lastDot = signedToken.lastIndexOf(TOKEN_SEPARATOR);
        String rawToken = signedToken.substring(0, lastDot);
        String providedSignature = signedToken.substring(lastDot + 1);

        String expectedSignature = computeHmac(rawToken);
        return MessageDigest.isEqual(
                providedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String hashForStorage(String rawToken) {
        return sha256(rawToken);
    }

    @Override
    public String buildResetLink(String rawToken) {
        String base = passwordResetProperties.getFrontendUrl();
        String path = passwordResetProperties.getResetPath();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path + "?token=" + rawToken;
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    getSecretKeyBytes(),
                    HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception ex) {
            log.error("HMAC computation failed", ex);
            throw new IllegalStateException("HMAC computation failed", ex);
        }
    }

    private byte[] getSecretKeyBytes() {
        return passwordResetProperties.getTokenSecret().getBytes(StandardCharsets.UTF_8);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
