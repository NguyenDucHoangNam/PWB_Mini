package com.pwb.iam.application.scheduler;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.iam.infrastructure.config.IamReminderProperties;
import com.pwb.infra.mail.api.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pwb.iam.reminder.enabled", havingValue = "true", matchIfMissing = true)
public class ProvisionalUsernameReminderScheduler {

    private final UserRepository userRepository;
    private final EmailDeliveryPort emailDeliveryPort;
    private final IamReminderProperties properties;

    @Scheduled(cron = "${pwb.iam.reminder.cron:0 0 9 * * *}")
    public void sendReminders() {
        int minAgeHours = properties.minAgeHours() <= 0 ? 24 : properties.minAgeHours();
        Instant cutoff = Instant.now().minusSeconds(minAgeHours * 3600L);
        List<User> candidates = userRepository.findProvisionalUsersCreatedBefore(cutoff);

        if (candidates.isEmpty()) {
            log.debug("ProvisionalUsernameReminder: no candidates to remind");
            return;
        }

        String baseUrl = properties.frontendUrl() == null ? "http://localhost:3000" : properties.frontendUrl();
        String path = properties.completeProfilePath() == null ? "/complete-profile" : properties.completeProfilePath();
        String updateLink = baseUrl + path;

        for (User user : candidates) {
            Map<String, String> variables = Map.of(
                    "currentUsername", user.getUsername() == null ? "" : user.getUsername(),
                    "updateLink", updateLink
            );
            emailDeliveryPort.enqueue(new EmailEnqueueCommand(
                    user.getUserId(),
                    user.getEmail().value(),
                    EmailTemplate.PROVISIONAL_USERNAME_REMINDER,
                    variables,
                    null
            ));
        }
        log.info("ProvisionalUsernameReminder: dispatched reminders count={}", candidates.size());
    }
}
