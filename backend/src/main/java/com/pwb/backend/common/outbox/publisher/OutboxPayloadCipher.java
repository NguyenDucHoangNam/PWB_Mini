package com.pwb.backend.common.outbox.publisher;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
@Component
public class OutboxPayloadCipher {

    private static final String CIPHER_PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int RAW_KEY_BYTES = 32;

    private final String keyMaterial;
    private SecretKeySpec keySpec;

    public OutboxPayloadCipher(@Value("${app.security.outbox.encryption-key:}") String keyMaterial) {
        this.keyMaterial = keyMaterial;
    }

    @PostConstruct
    void init() {
        if (keyMaterial == null || keyMaterial.isBlank()) {
            log.warn("OUTBOX_PAYLOAD_CIPHER_DISABLED reason=missing-key outbox-pii-will-be-persisted-as-plaintext");
            this.keySpec = null;
            return;
        }
        byte[] key = toRawKey(keyMaterial);
        if (key.length != RAW_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.security.outbox.encryption-key must decode to " + RAW_KEY_BYTES + " bytes (got " + key.length + ")");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
        log.info("OUTBOX_PAYLOAD_CIPHER_ENABLED");
    }

    public boolean isEnabled() {
        return keySpec != null;
    }

    public String encrypt(String plaintext) {
        if (!isEnabled() || plaintext == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            java.security.SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Outbox payload encryption failed", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEnabled() || !stored.startsWith(CIPHER_PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(CIPHER_PREFIX.length()));
            if (combined.length < IV_LENGTH_BYTES + TAG_LENGTH_BITS / 8) {
                throw new IllegalStateException("Encrypted outbox payload too short");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Outbox payload decryption failed", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(CIPHER_PREFIX);
    }

    private byte[] toRawKey(String raw) {
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            if (decoded.length == RAW_KEY_BYTES) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.Arrays.copyOf(hashed, RAW_KEY_BYTES);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
