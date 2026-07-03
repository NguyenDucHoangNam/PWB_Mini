package com.pwb.backend.modules.iam.internal.service;

import com.pwb.backend.modules.iam.internal.config.IamProperties;
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
  void testGetStoredOtp_returnsValue() {
    String email = "test@gmail.com";
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:registration:" + email)).thenReturn("123456");

    String stored = otpService.getStoredOtp(email);

    assertEquals("123456", stored);
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
