package com.pwb.backend.iam.internal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.pwb.backend.iam.api.dto.request.DeleteAccountRequest;
import com.pwb.backend.iam.api.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.factory.OutboxEventFactory;
import com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor;
import com.pwb.backend.iam.internal.helper.LoginLockoutHelper;
import com.pwb.backend.iam.internal.mapper.UserMapper;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final StringRedisTemplate redisTemplate;
  private final SessionService sessionService;
  private final JwtService jwtService;
  private final IamProperties iamProperties;
  private final OutboxEventFactory outboxEventFactory;
  private final UserMapper userMapper;
  private final PlatformTransactionManager transactionManager;
  private final LoginLockoutHelper loginLockoutHelper;
  private GoogleIdTokenVerifier googleVerifier;

  @PostConstruct
  public void init() {
    if (iamProperties.getGoogle() != null && iamProperties.getGoogle().getClientId() != null) {
      googleVerifier = new GoogleIdTokenVerifier.Builder(
          new NetHttpTransport(),
          GsonFactory.getDefaultInstance())
          .setAudience(Collections.singletonList(iamProperties.getGoogle().getClientId()))
          .build();
    }
  }

  public void deleteAccount(DeleteAccountRequest request, String expiredAccessTokenHeader, String currentRefreshToken, HttpServletResponse response) {
    if (expiredAccessTokenHeader == null || !expiredAccessTokenHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String expiredToken = expiredAccessTokenHeader.substring(7);

    String email;
    try {
      email = jwtService.extractEmailFromExpiredToken(expiredToken);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token signature or format");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() == UserStatus.PENDING_DELETION) {
      throw new BusinessException(ErrorCode.DELETION_ALREADY_REQUESTED, "Account deletion has already been requested");
    }

    String userId = user.getId();
    loginLockoutHelper.ensureNotLocked(redisTemplate, userId);

    if (user.getPassword() != null) {
      if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
        loginLockoutHelper.recordFailure(redisTemplate, userId);
        throw new BusinessException(ErrorCode.INVALID_PASSWORD, "Incorrect password confirmation");
      }
      redisTemplate.delete(loginLockoutHelper.attemptsKey(userId));
    } else {
      if (request.idToken() == null) {
        throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN, "Google idToken is required");
      }
      try {
        GoogleIdToken googleIdToken = googleVerifier.verify(request.idToken());
        if (googleIdToken == null) {
          throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN, "Invalid Google idToken");
        }
      } catch (Exception e) {
        throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN, "Invalid Google idToken");
      }
    }

    user.setStatus(UserStatus.PENDING_DELETION);
    user.setDeletionRequestedAt(Instant.now());

    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.executeWithoutResult(status -> {
      userRepository.save(user);

      int graceDays = iamProperties.getAccountDeletionGraceDays();
      ZoneId deletionZone;
      try {
        deletionZone = ZoneId.of(iamProperties.getAccountDeletion().getTimezone());
      } catch (Exception zoneEx) {
        log.warn("Invalid account-deletion.timezone '{}', falling back to UTC",
            iamProperties.getAccountDeletion().getTimezone(), zoneEx);
        deletionZone = ZoneId.of("UTC");
      }
      DateTimeFormatter formatter = DateTimeFormatter
          .ofPattern("yyyy-MM-dd HH:mm:ss")
          .withZone(deletionZone);
      String deletionDate = formatter.format(Instant.now().plus(graceDays, ChronoUnit.DAYS));

      outboxEventFactory.accountDeletionRequested(user, deletionDate,
          LocaleContextHolder.getLocale().getLanguage());
    });

    sessionService.revokeAllUserSessions(userId);
    redisTemplate.delete("user:last_login:" + userId);

    sessionService.clearRefreshCookie(response);
  }

  @Transactional
  public UserProfileResponse cancelDeletion(String authHeader) {
    String email = JwtPrincipalExtractor.requireEmailFromHeader(authHeader, jwtService);

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() == UserStatus.ANONYMIZED) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Account has been permanently deleted and cannot be restored");
    }

    if (user.getStatus() != UserStatus.PENDING_DELETION) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Account is not pending deletion");
    }

    Instant now = Instant.now();
    int graceDays = iamProperties.getAccountDeletionGraceDays();
    if (user.getDeletionRequestedAt() != null
        && now.isAfter(user.getDeletionRequestedAt().plus(graceDays, ChronoUnit.DAYS))) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "The " + graceDays + "-day grace period has elapsed, account cannot be recovered");
    }

    user.setStatus(UserStatus.ACTIVE);
    user.setDeletionRequestedAt(null);
    User saved = userRepository.save(user);

    outboxEventFactory.accountDeletionCancelled(saved, LocaleContextHolder.getLocale().getLanguage());

    return userMapper.toUserProfileResponse(saved);
  }

  public TriggerAnonymizationResponse triggerAnonymization() {
    long startTime = System.currentTimeMillis();
    int graceDays = iamProperties.getAccountDeletionGraceDays();
    Instant cutoff = Instant.now().minus(graceDays, ChronoUnit.DAYS);
    int batchSize = iamProperties.getAnonymization().getBatchSize();
    List<User> usersToAnonymize = userRepository.findUsersPendingDeletionBefore(cutoff, PageRequest.of(0, batchSize));

    TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionManager);
    requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    int count = 0;
    for (User user : usersToAnonymize) {
      try {
        requiresNewTemplate.executeWithoutResult(status -> anonymizeUser(user));
        count++;
        log.info("USER_ANONYMIZED_SUCCESS: userId={}", com.pwb.backend.iam.internal.helper.PiiScrubber.userRef(user.getId()));
      } catch (Exception e) {
        log.error("USER_ANONYMIZATION_FAILED: userId={}", com.pwb.backend.iam.internal.helper.PiiScrubber.userRef(user.getId()), e);
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    return new TriggerAnonymizationResponse(count, duration, "COMPLETED");
  }

  public void anonymizeUser(User user) {
    String userId = user.getId();
    sessionService.revokeAllUserSessions(userId);
    redisTemplate.delete("user:last_login:" + userId);
    loginLockoutHelper.clear(redisTemplate, userId);

    user.setUsername("deleted_user_" + userId);
    user.setEmail("deleted_" + userId + "@pwbmini.com");
    user.setPassword(null);
    user.setFullName(null);
    user.setPhone(null);
    user.setAvatarUrl(null);
    user.setOauthProvider(null);
    user.setOauthId(null);
    user.setStatus(UserStatus.ANONYMIZED);
    user.setDeleted(true);
    user.setDeletedAt(Instant.now());

    userRepository.save(user);

    outboxEventFactory.accountAnonymized(userId, "deleted_" + userId + "@pwbmini.com",
        LocaleContextHolder.getLocale().getLanguage());
  }
}
