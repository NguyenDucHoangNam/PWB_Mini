package com.pwb.iam.infrastructure.mail;

import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.infra.mail.api.EmailEventRequested;
import com.pwb.infra.mail.api.EmailPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEmailDeliveryAdapter implements EmailDeliveryPort {

    private final ThymeleafEmailRenderer thtmeleafEmailRenderer;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${pwb.mail.from:noreply@pwb.local}")
    private String defaultFrom;

    @Override
    public void enqueue(EmailEnqueueCommand command) {
        String locale = command.locale() == null || command.locale().isBlank() ? "vi" : command.locale();
        Map<String, String> variables = command.variables() == null ? Map.of() : command.variables();

        String subject = thtmeleafEmailRenderer.resolveSubject(command.template(), locale);
        String htmlBody = thtmeleafEmailRenderer.renderHtml(command.template(), variables, locale);
        String textBody = thtmeleafEmailRenderer.renderText(command.template(), variables, locale);

        EmailPayload payload = new EmailPayload(
                command.template().name(),
                command.email(),
                command.userId(),
                variables,
                locale,
                subject,
                htmlBody,
                textBody,
                defaultFrom
        );

        log.info("Email enqueued via outbox: userId={} template={} locale={}",
                command.userId(), command.template(), locale);
        applicationEventPublisher.publishEvent(new EmailEventRequested(payload));
    }
}