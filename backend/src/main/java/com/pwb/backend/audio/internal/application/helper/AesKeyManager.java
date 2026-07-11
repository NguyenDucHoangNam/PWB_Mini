package com.pwb.backend.audio.internal.application.helper;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.audio.internal.domain.exception.AudioErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AesKeyManager {

  private static final String KEY_PREFIX = "demo:key:";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final String ALGO = "AES/GCM/NoPadding";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);
  private static final int MIN_MASTER_KEY_BYTES = 16;

  private final AudioProperties audioProperties;
  private final StringRedisTemplate redisTemplate;
  private final SecureRandom secureRandom = new SecureRandom();

  public byte[] generateDemoKey() {
    int keyBytes = audioProperties.getStream().getHls().getAesKeyBytes();
    byte[] key = new byte[keyBytes];
    secureRandom.nextBytes(key);
    return key;
  }

  public byte[] encryptMaster(byte[] demoKey) {
    String masterRaw = audioProperties.getAes().getMasterKey();
    if (masterRaw == null || masterRaw.isBlank()) {
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED,
          "AUDIO_AES_MASTER_KEY is not configured");
    }
    byte[] masterBytes = masterRaw.getBytes(StandardCharsets.UTF_8);
    if (masterBytes.length < MIN_MASTER_KEY_BYTES) {
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED,
          "AUDIO_AES_MASTER_KEY must be at least " + MIN_MASTER_KEY_BYTES + " bytes");
    }
    try {
      byte[] derivedKey = MessageDigest.getInstance("SHA-256")
          .digest(masterBytes);
      SecretKeySpec keySpec = new SecretKeySpec(derivedKey, "AES");
      byte[] nonce = new byte[NONCE_BYTES];
      secureRandom.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] ct = cipher.doFinal(demoKey);
      ByteBuffer bb = ByteBuffer.allocate(nonce.length + ct.length);
      bb.put(nonce);
      bb.put(ct);
      return bb.array();
    } catch (Exception ex) {
      log.error("AES master encryption failed", ex);
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED,
          "AES encryption failed");
    }
  }

  public byte[] decryptMaster(byte[] encryptedCombined) {
    String masterRaw = audioProperties.getAes().getMasterKey();
    if (masterRaw == null || masterRaw.isBlank()) {
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED,
          "AUDIO_AES_MASTER_KEY is not configured");
    }
    byte[] masterBytes = masterRaw.getBytes(StandardCharsets.UTF_8);
    if (masterBytes.length < MIN_MASTER_KEY_BYTES) {
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED,
          "AUDIO_AES_MASTER_KEY must be at least " + MIN_MASTER_KEY_BYTES + " bytes");
    }
    if (encryptedCombined == null || encryptedCombined.length < NONCE_BYTES + 16) {
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED,
          "Encrypted key payload too short");
    }
    try {
      byte[] derivedKey = MessageDigest.getInstance("SHA-256")
          .digest(masterBytes);
      SecretKeySpec keySpec = new SecretKeySpec(derivedKey, "AES");
      byte[] nonce = new byte[NONCE_BYTES];
      byte[] ct = new byte[encryptedCombined.length - NONCE_BYTES];
      System.arraycopy(encryptedCombined, 0, nonce, 0, NONCE_BYTES);
      System.arraycopy(encryptedCombined, NONCE_BYTES, ct, 0, ct.length);
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
      return cipher.doFinal(ct);
    } catch (Exception ex) {
      log.error("AES master decryption failed", ex);
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED,
          "AES decryption failed");
    }
  }

  public void cacheInRedis(String demoId, byte[] keyBytes, int version) {
    String key = KEY_PREFIX + demoId;
    String keyField = "keyBytes:" + version;
    redisTemplate.opsForHash().put(key, keyField, Base64.getEncoder().encodeToString(keyBytes));
    redisTemplate.opsForHash().put(key, "version", String.valueOf(version));
    redisTemplate.expire(key, CACHE_TTL);
    log.info("Cached AES key for demo={} (version={})", demoId, version);
  }

  public Optional<byte[]> getFromCache(String demoId, int version) {
    String key = KEY_PREFIX + demoId;
    String value = (String) redisTemplate.opsForHash().get(key, "keyBytes:" + version);
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(Base64.getDecoder().decode(value));
  }

  public void evict(String demoId) {
    redisTemplate.delete(KEY_PREFIX + demoId);
  }

  public String toHex(byte[] keyBytes) {
    return HexFormat.of().formatHex(keyBytes);
  }
}
