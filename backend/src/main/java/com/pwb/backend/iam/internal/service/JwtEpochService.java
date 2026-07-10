package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Tracks a global JWT "epoch" stored in Redis. Whenever the operator rotates
 * the JWT signing secret they should call {@link #rotateEpoch()} which:
 *
 * <ul>
 *   <li>increments the epoch value in Redis,</li>
 *   <li>adds a wildcard blacklist key under {@code session:blacklist_token:*}
 *     that effectively invalidates all previously-issued access tokens.</li>
 * </ul>
 *
 * <p>This is the only reliable way to invalidate outstanding JWTs after a
 * secret rotation, since JWTs are stateless and cannot be force-expired from
 * the server alone. The {@link com.pwb.backend.iam.internal.config.JwtAuthenticationFilter}
 * checks the epoch on every request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtEpochService {

  private static final String EPOCH_KEY = "jwt:epoch";
  private static final String INVALIDATE_ALL_KEY = "jwt:invalidate_all_before";

  private final StringRedisTemplate redisTemplate;
  private final IamProperties iamProperties;

  @PostConstruct
  public void ensureEpochInitialized() {
    if (redisTemplate == null) {
      return;
    }
    Boolean exists = redisTemplate.hasKey(EPOCH_KEY);
    if (exists == null || !exists) {
      redisTemplate.opsForValue().set(EPOCH_KEY, "1");
      log.info("JWT epoch initialized to 1");
    }
  }

  /**
   * Returns the current epoch value. Tokens minted after this point are
   * considered valid.
   *
   * <p>Safe to call with a null {@link StringRedisTemplate} (returns 1L)
   * so unit tests can construct this service without a Redis dependency.
   */
  public long currentEpoch() {
    if (redisTemplate == null) {
      return 1L;
    }
    String value = redisTemplate.opsForValue().get(EPOCH_KEY);
    if (value == null) {
      redisTemplate.opsForValue().set(EPOCH_KEY, "1");
      return 1L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return 1L;
    }
  }

  /**
   * Bumps the epoch, invalidating all previously-issued access tokens until
   * their natural expiry.
   *
   * @return the new epoch value.
   */
  public long rotateEpoch() {
    if (redisTemplate == null) {
      return 1L;
    }
    Long next = redisTemplate.opsForValue().increment(EPOCH_KEY);
    if (next != null) {
      log.warn("JWT secret rotation epoch bumped to {}", next);
      markInvalidationBoundary(next - 1);
      return next;
    }
    return currentEpoch();
  }

  private void markInvalidationBoundary(long previousEpoch) {
    redisTemplate.opsForValue().set(INVALIDATE_ALL_KEY, String.valueOf(previousEpoch),
        java.time.Duration.ofDays(7));
  }
}