package com.pwb.infra.mail.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.mail.api.EmailPayload;
import com.pwb.infra.mail.properties.MailProperties;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.mail.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MailKafkaConsumer {

    private static final String FALLBACK_FROM = "noreply@pwb.local";
    private static final String LOGO_CONTENT_ID = "pwb-logo";
    private static final String LOGO_RESOURCE_PATH = "static/images/pwb-logo.png";
    private static final String IMAGE_PNG_MIME = "image/png";

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
                helper.setText(payload.textBody(), buildHtmlBodyWithLogo(payload.htmlBody()));
            } else {
                helper.setText(buildHtmlBodyWithLogo(payload.htmlBody()), true);
            }

            mailSender.send(message);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException ex) {
            log.warn("Mail send failed: to={} template={} reason={}",
                    payload.toEmail(), payload.template(), ex.getMessage());
            throw new RuntimeException("Mail send failed", ex);
        }
    }

    private String buildHtmlBodyWithLogo(String htmlBody) {
        try {
            byte[] logoBytes = loadLogoBytes();
            if (logoBytes == null) {
                return htmlBody;
            }

            MimeMultipart rootMultipart = new MimeMultipart("related");
            MimeMultipart htmlMultipart = new MimeMultipart("alternative");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlMultipart);
            rootMultipart.addBodyPart(htmlPart);

            MimeBodyPart imagePart = new MimeBodyPart();
            DataSource imageDs = new ByteArrayDataSource(logoBytes, IMAGE_PNG_MIME);
            imagePart.setDataHandler(new DataHandler(imageDs));
            imagePart.setHeader("Content-ID", "<" + LOGO_CONTENT_ID + ">");
            imagePart.setDisposition(MimeMessage.INLINE);
            rootMultipart.addBodyPart(imagePart);

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(htmlBody, "UTF-8", "html");
            htmlMultipart.addBodyPart(textPart);

            MimeMessage tempMessage = mailSender.createMimeMessage();
            tempMessage.setContent(rootMultipart);
            return (String) tempMessage.getContent();
        } catch (Exception ex) {
            log.warn("Failed to embed logo, sending without logo: {}", ex.getMessage());
            return htmlBody;
        }
    }

    private byte[] loadLogoBytes() {
        try (InputStream is = new ClassPathResource(LOGO_RESOURCE_PATH).getInputStream()) {
            return is.readAllBytes();
        } catch (IOException ex) {
            log.warn("Logo resource not found: {}, email will be sent without logo", LOGO_RESOURCE_PATH);
            return null;
        }
    }
}