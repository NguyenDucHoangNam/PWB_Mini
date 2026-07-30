package com.pwb.infra.mail.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.mail.api.EmailPayload;
import com.pwb.infra.mail.properties.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.mail.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MailKafkaConsumer {

    private static final String FALLBACK_FROM = "noreply@pwb.local";
    private static final String TEXT_MIME_TYPE = "text/plain";
    private static final String HTML_MIME_TYPE = "text/html";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.email:notification.email.v1}",
            groupId = "${pwb.mail.consumer.group:pwb-mail-consumer}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEmail(ConsumerRecord<String, String> record) {
        EmailPayload payload = parsePayload(record);
        validatePayload(payload);
        sendEmail(payload);
        log.debug("Mail sent: key={} to={} template={}", record.key(), payload.toEmail(), payload.template());
    }

    private EmailPayload parsePayload(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), EmailPayload.class);
        } catch (Exception ex) {
            log.warn("Mail payload parse failed: key={} reason={}", record.key(), ex.getMessage());
            throw new MailPayloadException("Mail payload parse failed: " + ex.getMessage(), ex);
        }
    }

    private void validatePayload(EmailPayload payload) {
        if (payload == null) {
            throw new MailPayloadException("Mail payload is null");
        }
        if (payload.toEmail() == null || payload.toEmail().isBlank()) {
            throw new MailTemplateException("Mail payload toEmail is blank");
        }
        if (payload.subject() == null || payload.subject().isBlank()) {
            throw new MailTemplateException("Mail payload subject is blank (template not rendered?)");
        }
        if (payload.htmlBody() == null || payload.htmlBody().isBlank()) {
            throw new MailTemplateException("Mail payload htmlBody is blank (template not rendered?)");
        }
    }

    private void sendEmail(EmailPayload payload) {
        try {
            String fromAddress = payload.from() != null && !payload.from().isBlank()
                    ? payload.from()
                    : (mailProperties.getFromAddress() != null && !mailProperties.getFromAddress().isBlank()
                        ? mailProperties.getFromAddress()
                        : FALLBACK_FROM);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, mailProperties.getFromName() == null ? "" : mailProperties.getFromName());
            helper.setTo(payload.toEmail());
            helper.setSubject(payload.subject());

            if (payload.textBody() != null && !payload.textBody().isBlank()) {
                helper.setText(payload.textBody(), payload.htmlBody());
            } else {
                helper.setText(payload.htmlBody(), true);
            }

            mailSender.send(message);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException ex) {
            log.warn("Mail send failed: to={} template={} reason={}",
                    payload.toEmail(), payload.template(), ex.getMessage());
            throw new RuntimeException("Mail send failed", ex);
        }
    }

    private String resolveMimeType(String body) {
        return body != null && body.contains("<html") ? HTML_MIME_TYPE : TEXT_MIME_TYPE;
    }
}