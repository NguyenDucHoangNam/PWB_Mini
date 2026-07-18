package com.pwb.notification.api;

import com.pwb.notification.api.event.EmailRequestedIntegrationEvent;
import com.pwb.notification.infrastructure.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacadeImpl implements NotificationFacade {

    private final SpringTemplateEngine emailTemplateEngine;
    private final MailService mailService;
    private final MessageSource messageSource;

    @Override
    public void consumeEmailRequest(EmailRequestedIntegrationEvent event) {
        Locale locale = event.locale() != null ? event.locale() : Locale.ENGLISH;

        Context context = new Context(locale);
        if (event.model() != null) {
            event.model().forEach(context::setVariable);
        }

        String htmlBody = emailTemplateEngine.process(event.templateName(), context);

        String subject = messageSource.getMessage(
                event.subjectKey(),
                null,
                event.subjectKey(),
                locale);

        mailService.send(event.to(), subject, htmlBody);
        log.info("Email sent: to={}, subject={}, template={}",
                event.to(), subject, event.templateName());
    }
}
