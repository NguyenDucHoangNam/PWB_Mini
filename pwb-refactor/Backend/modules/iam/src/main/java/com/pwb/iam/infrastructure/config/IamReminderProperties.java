package com.pwb.iam.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pwb.iam.reminder")
public record IamReminderProperties(
        String cron,
        String frontendUrl,
        String completeProfilePath,
        int minAgeHours
) {
}
