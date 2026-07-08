package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

  private OtpService otpService;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private IamProperties iamProperties;

  @BeforeEach
  void setUp() {
    iamProperties = new IamProperties();
    iamProperties.getOtp().setExpiration(300); // 5 mins
    iamProperties.getOtp().setCooldown(60); // 60s
    iamProperties.getOtp().setMaxAttempts(5);

    otpService = new OtpService(redisTemplate, iamProperties);
  }

  @Test
  void testGenerateOtp_returnsSixDigitString() {
    String otp = otpService.generateOtp();
    assertNotNull(otp);
    assertEquals(6, otp.length());
    assertTrue(otp.matches("^[0-9]{6}$"));
  }

  @Test
  void testStoreOtp_executesPipelinedRedisCommand() {
    String email = "test@gmail.com";
    String otp = "123456";

    otpService.storeOtp(email, otp);

    verify(redisTemplate).executePipelined(any(RedisCallback.class));
  }

  @Test
  void testStoreOtpWithAttemptsReset_executesPipelinedRedisCommand() {
    String email = "test@gmail.com";
    String otp = "123456";

    otpService.storeOtpWithAttemptsReset(email, otp);

    verify(redisTemplate).executePipelined(any(RedisCallback.class));
  }

  @Test
  void testVerifyOtp_correctCode_returnsTrue() {
    String email = "test@gmail.com";
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String hash = computeExpectedHash(email, "123456");
    when(valueOperations.get("otp:registration:" + email)).thenReturn(hash);

    assertTrue(otpService.verifyOtp(email, "123456"));
  }

  @Test
  void testVerifyOtp_wrongCode_returnsFalse() {
    String email = "test@gmail.com";
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String hash = computeExpectedHash(email, "123456");
    when(valueOperations.get("otp:registration:" + email)).thenReturn(hash);

    assertFalse(otpService.verifyOtp(email, "000000"));
  }

  @Test
  void testVerifyOtp_emptyStore_returnsFalse() {
    String email = "test@gmail.com";
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:registration:" + email)).thenReturn(null);

    assertFalse(otpService.verifyOtp(email, "123456"));
  }

  @Test
  void testVerifyOtp_emailCaseInsensitive() {
    String storedEmail = "test@gmail.com";
    String submittedEmail = "TEST@GMAIL.COM";
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String hash = computeExpectedHash(storedEmail, "123456");
    when(valueOperations.get("otp:registration:" + submittedEmail)).thenReturn(null);

    assertFalse(otpService.verifyOtp(submittedEmail, "123456"));
  }

  private static String computeExpectedHash(String email, String code) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      digest.update(email.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      byte[] hash = digest.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Test
  void testGetAttempts_corruptedValue_returnsZero() {
    String email = "test@gmail.com";
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:attempts:" + email)).thenReturn("not-a-number");

    assertEquals(0L, otpService.getAttempts(email));
  }

  @Test
  void testGetAttempts_nullValue_returnsZero() {
    String email = "test@gmail.com";
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:attempts:" + email)).thenReturn(null);

    assertEquals(0L, otpService.getAttempts(email));
  }

  @Test
  void testCheckCooldown_whenKeyExists_returnsTrue() {
    String email = "test@gmail.com";
    when(redisTemplate.hasKey("otp:cooldown:" + email)).thenReturn(true);

    assertTrue(otpService.checkCooldown(email));
  }

  @Test
  void testCheckCooldown_whenKeyDoesNotExist_returnsFalse() {
    String email = "test@gmail.com";
    when(redisTemplate.hasKey("otp:cooldown:" + email)).thenReturn(false);

    assertFalse(otpService.checkCooldown(email));
  }

  @Test
  void testIncrementAttempts_firstAttempt_setsExpiry() {
    String email = "test@gmail.com";
    String key = "otp:attempts:" + email;
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(key)).thenReturn(1L);

    long attempts = otpService.incrementAttempts(email);

    assertEquals(1L, attempts);
    verify(redisTemplate).expire(eq(key), any(Duration.class));
  }

  @Test
  void testIncrementAttempts_subsequentAttempt_doesNotSetExpiry() {
    String email = "test@gmail.com";
    String key = "otp:attempts:" + email;
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(key)).thenReturn(2L);

    long attempts = otpService.incrementAttempts(email);

    assertEquals(2L, attempts);
  }

  @Test
  void testDeleteAllOtpKeys_callsDelete() {
    String email = "test@gmail.com";

    otpService.deleteAllOtpKeys(email);

    verify(redisTemplate).delete(List.of(
        "otp:registration:" + email,
        "otp:cooldown:" + email,
        "otp:attempts:" + email
    ));
  }
}
