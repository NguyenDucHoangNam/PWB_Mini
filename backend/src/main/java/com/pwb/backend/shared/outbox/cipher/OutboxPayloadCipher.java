package com.pwb.backend.shared.outbox.cipher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class OutboxPayloadCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final String PREFIX = "enc:";
    private static final String VERSION_DELIMITER = ":";

    private final SecureRandom random = new SecureRandom();
    private final boolean enabled;
    private final int activeKeyVersion;

    private final Map<Integer, SecretKey> keysByVersion;

    public OutboxPayloadCipher(
        @Value("${app.outbox.encryption-key:}") String primaryEncryptionKey,
        @Value("${app.outbox.key-version:1}") int activeKeyVersion,
        @Value("${app.outbox.legacy-keys:}") String legacyKeysCsv
    ) {
        this.activeKeyVersion = activeKeyVersion;
        Map<Integer, SecretKey> keys = new LinkedHashMap<>();
        if (primaryEncryptionKey == null || primaryEncryptionKey.isBlank()) {
            this.enabled = false;
            log.warn("Outbox payload encryption is DISABLED (key not configured)");
        } else {
            try {
                keys.put(activeKeyVersion, deriveKey(primaryEncryptionKey));
                this.enabled = true;
                log.info("Outbox payload encryption ENABLED (activeKeyVersion={})", activeKeyVersion);
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot initialize outbox cipher", ex);
            }
        }

        if (enabled && legacyKeysCsv != null && !legacyKeysCsv.isBlank()) {
            for (String entry : legacyKeysCsv.split(",")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int colon = trimmed.lastIndexOf(':');
                if (colon <= 0 || colon == trimmed.length() - 1) {
                    log.warn("Skipping malformed legacy-keys entry (expected 'version:secret'): {}",
                        trimmed);
                    continue;
                }
                int version;
                try {
                    version = Integer.parseInt(trimmed.substring(0, colon).trim());
                } catch (NumberFormatException nfe) {
                    log.warn("Skipping legacy-keys entry with non-numeric version: {}", trimmed);
                    continue;
                }
                if (keys.containsKey(version)) {
                    continue;
                }
                try {
                    keys.put(version, deriveKey(trimmed.substring(colon + 1).trim()));
                    log.info("Registered legacy outbox cipher key version={}", version);
                } catch (Exception ex) {
                    log.warn("Skipping legacy-keys entry (cannot derive): {}", trimmed);
                }
            }
        }
        this.keysByVersion = Collections.unmodifiableMap(keys);
    }

    private static SecretKey deriveKey(String secret) throws Exception {
        byte[] derived = MessageDigest.getInstance("SHA-256")
            .digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(derived, "AES");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int keyVersion() {
        return activeKeyVersion;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (!enabled) {
            return plaintext;
        }
        SecretKey key = keysByVersion.get(activeKeyVersion);
        if (key == null) {
            throw new IllegalStateException(
                "Active outbox cipher key version " + activeKeyVersion + " is not registered");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return PREFIX + "v" + activeKeyVersion + VERSION_DELIMITER
                + Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Outbox payload encryption failed", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!enabled || !stored.startsWith(PREFIX)) {

            if (enabled && !stored.startsWith(PREFIX)) {
                log.debug("Outbox decrypt: payload has no 'enc:' prefix, returning as plaintext");
            }
            return stored;
        }
        String body = stored.substring(PREFIX.length());
        Integer version = null;
        int colon = body.indexOf(VERSION_DELIMITER);
        if (colon > 0) {
            String head = body.substring(0, colon);
            if (head.startsWith("v")) {
                try {
                    version = Integer.parseInt(head.substring(1));
                    body = body.substring(colon + 1);
                } catch (NumberFormatException nfe) {

                    version = null;
                }
            }
        }
        SecretKey key = pickKey(version);
        try {
            byte[] all = Base64.getDecoder().decode(body);
            if (all.length <= NONCE_BYTES) {
                throw new IllegalStateException("Ciphertext too short");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ct = new byte[all.length - NONCE_BYTES];
            System.arraycopy(all, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(all, NONCE_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Outbox payload decryption failed", ex);
        }
    }

    private SecretKey pickKey(Integer payloadVersion) {

        if (payloadVersion == null) {
            SecretKey legacy = keysByVersion.get(1);
            if (legacy == null) {
                log.warn("Outbox payload uses legacy unversioned format but no v1 key is configured; "
                    + "falling back to active key version {}", activeKeyVersion);
                return keysByVersion.get(activeKeyVersion);
            }
            log.debug("Outbox decrypt: legacy v1 payload, using v1 key");
            return legacy;
        }
        SecretKey key = keysByVersion.get(payloadVersion);
        if (key == null) {
            log.warn("Outbox payload uses keyVersion={} which is not configured; "
                + "falling back to active key version {}", payloadVersion, activeKeyVersion);
            return keysByVersion.get(activeKeyVersion);
        }
        return key;
    }
}