package com.pwb.backend.iam.internal.publisher;

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

/**
 * Encrypts outbox payloads at rest. Each encrypted payload is prefixed with
 * a random 12-byte nonce and authenticated with AES-GCM.
 *
 * <p>The encryption key is derived from a configured property
 * {@code app.iam.outbox.encryption-key}; if it is missing, encryption is
 * disabled and payloads are stored as plaintext (with a WARN logged). This
 * keeps local development frictionless while still allowing production to
 * enable at-rest encryption.
 */
@Component
public class OutboxPayloadCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGO = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();
    private final boolean enabled;

    public OutboxPayloadCipher(
        @Value("${app.iam.outbox.encryption-key:}") String encryptionKey
    ) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            this.key = null;
            this.enabled = false;
        } else {
            try {
                byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
                this.key = new SecretKeySpec(derived, "AES");
                this.enabled = true;
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot initialize outbox cipher", ex);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (!enabled) return plaintext;
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return "enc:" + Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Outbox payload encryption failed", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!enabled || !stored.startsWith("enc:")) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(4));
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
}