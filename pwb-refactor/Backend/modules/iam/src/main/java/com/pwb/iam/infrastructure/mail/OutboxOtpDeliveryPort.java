package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailEventRequested;
import com.pwb.infra.mail.api.EmailPayload;
import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.iam.domain.service.OtpDeliveryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxOtpDeliveryPort implements OtpDeliveryPort {

    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final String DEFAULT_LOCALE = "vi";

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public DeliveryResult deliver(UUID userId, String email, String purpose, String code) {
        String resolvedPurpose = purpose == null ? "REGISTER" : purpose;
        EmailTemplate template = mapTemplate(resolvedPurpose);

        Map<String, String> variables = (code == null || code.isBlank())
                ? Map.of()
                : Map.of("code", code, "ttlMinutes", "10");

        EmailPayload payload = new EmailPayload(
                template.name(),
                email,
                userId,
                variables,
                DEFAULT_LOCALE
        );

        log.info("OTP enqueued via outbox: userId={} purpose={} template={}",
                userId, resolvedPurpose, template);
        applicationEventPublisher.publishEvent(new EmailEventRequested(payload));
        return DeliveryResult.ok(COOLDOWN);
    }

    private EmailTemplate mapTemplate(String purpose) {
        return switch (purpose) {
            case "REGISTER" -> EmailTemplate.OTP_REGISTER;
            case "FORGOT_PASSWORD", "PASSWORD_RESET", "RESET_PASSWORD" -> EmailTemplate.PASSWORD_RESET;
            default -> EmailTemplate.OTP_REGISTER;
        };
    }
}