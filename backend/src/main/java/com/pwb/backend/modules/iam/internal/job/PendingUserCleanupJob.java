package com.pwb.backend.modules.iam.internal.job;

import com.pwb.backend.modules.iam.internal.model.User;
import com.pwb.backend.modules.iam.internal.repository.UserRepository;
import com.pwb.backend.modules.iam.internal.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingUserCleanupJob {

  private static final long EXPIRY_HOURS = 24;

  private final UserRepository userRepository;
  private final OtpService otpService;

  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void cleanupExpiredPendingUsers() {
    Instant cutoff = Instant.now().minus(EXPIRY_HOURS, ChronoUnit.HOURS);
    List<User> expiredUsers = userRepository.findExpiredPendingUsers(cutoff);

    if (expiredUsers.isEmpty()) {
      return;
    }

    for (User user : expiredUsers) {
      otpService.deleteAllOtpKeys(user.getEmail());
    }

    List<String> ids = expiredUsers.stream()
        .map(User::getId)
        .toList();

    userRepository.hardDeleteByIds(ids);

    log.info("Cleaned up {} expired pending users", expiredUsers.size());
  }
}
