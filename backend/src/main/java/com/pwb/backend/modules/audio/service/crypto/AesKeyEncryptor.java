package com.pwb.backend.modules.audio.service.crypto;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
@Slf4j
public class AesKeyEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${AUDIO_AES_MASTER_KEY}")
    private String masterKeyHex;

    public byte[] generateAes128Key() {
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        return key;
    }

    public byte[] encrypt(byte[] plaintext) {
        byte[] key = deriveMasterKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext);
            byte[] result = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherBytes, 0, result, iv.length, cipherBytes.length);
            return result;
        } catch (Exception ex) {
            log.warn("AES_ENCRYPT_FAILED reason={}", ex.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to encrypt AES key: " + ex.getMessage(), ex);
        }
    }

    public byte[] decrypt(byte[] payload) {
        byte[] key = deriveMasterKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
        } catch (Exception ex) {
            log.warn("AES_DECRYPT_FAILED reason={}", ex.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to decrypt AES key: " + ex.getMessage(), ex);
        }
    }

    public String encodeHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private byte[] deriveMasterKey() {
        if (masterKeyHex == null || masterKeyHex.isBlank()) {
            log.warn("AUDIO_AES_MASTER_KEY missing, using SHA-256(0) fallback (NOT FOR PRODUCTION)");
            byte[] fallback = new byte[32];
            return fallback;
        }
        try {
            byte[] raw = HexFormat.of().parseHex(masterKeyHex);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw);
        } catch (IllegalArgumentException ex) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return digest.digest(masterKeyHex.getBytes());
            } catch (Exception inner) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                        "Failed to derive AES master key", inner);
            }
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to derive AES master key", ex);
        }
    }
}