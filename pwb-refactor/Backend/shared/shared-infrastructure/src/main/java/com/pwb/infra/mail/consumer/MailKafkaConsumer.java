package com.pwb.infra.mail.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.mail.api.EmailPayload;
import com.pwb.infra.mail.properties.MailProperties;
import com.pwb.infra.mail.renderer.EmailTemplateRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.mail.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MailKafkaConsumer {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailTemplateRegistry templateRegistry;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.email:notification.email.v1}",
            groupId = "${pwb.mail.consumer.group:pwb-mail-consumer}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEmail(ConsumerRecord<String, String> record) {
        EmailPayload payload;
        try {
            payload = objectMapper.readValue(record.value(), EmailPayload.class);
        } catch (Exception ex) {
            log.warn("Mail payload parse failed: key={} reason={}", record.key(), ex.getMessage());
            throw new RuntimeException("Mail payload parse failed", ex);
        }

        try {
            sendEmail(payload);
            log.debug("Mail sent: key={} to={} template={}", record.key(), payload.toEmail(), payload.template());
        } catch (Exception ex) {
            log.warn("Mail send failed: to={} template={} reason={}", payload.toEmail(), payload.template(), ex.getMessage());
            throw new RuntimeException("Mail send failed", ex);
        }
    }

    private void sendEmail(EmailPayload payload) throws MessagingException, java.io.UnsupportedEncodingException {
        Map<String, String> variables = payload.variables();
        String locale = payload.locale() == null ? "vi" : payload.locale();
        String body = templateRegistry.render(payload.template(), variables, locale);
        String subject = templateRegistry.subject(payload.template(), variables, locale);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        if (mailProperties.getFromAddress() != null && !mailProperties.getFromAddress().isBlank()) {
            helper.setFrom(mailProperties.getFromAddress(),
                    mailProperties.getFromName() == null ? "" : mailProperties.getFromName());
        }
        helper.setTo(payload.toEmail());
        helper.setSubject(subject);
        helper.setText(body, true);
        mailSender.send(message);
    }
}