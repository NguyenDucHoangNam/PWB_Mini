package com.pwb.infra.mail.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.it.AbstractKafkaIT;
import com.pwb.infra.it.SharedInfraTestApp;
import com.pwb.infra.mail.api.EmailPayload;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = {SharedInfraTestApp.class, MailKafkaConsumerIT.TestConfig.class})
@ActiveProfiles("test")
@DisplayName("MailKafkaConsumer — process email events from Kafka topic")
class MailKafkaConsumerIT extends AbstractKafkaIT {

    @DynamicPropertySource
    static void overrideDeps(DynamicPropertyRegistry registry) {
        KAFKA.start();
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1025");
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("kafka.topics.email", () -> TOPIC);
        registry.add("kafka.topics.voiceProcessing", () -> "voice.processing.v1");
        registry.add("kafka.topics.iamAudit", () -> "iam.audit.v1");
        registry.add("pwb.outbox.enabled", () -> "false");
        registry.add("app.mail.from-address", () -> "test@pwb.local");
        registry.add("app.mail.from-name", () -> "PWB Test");
    }

    private static final String TOPIC = "mail-consumer-test-" + UUID.randomUUID();

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        JavaMailSender testJavaMailSender() {
            JavaMailSender sender = mock(JavaMailSender.class);
            org.mockito.Mockito.when(sender.createMimeMessage())
                    .thenAnswer(inv -> new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
            return sender;
        }
    }

    @MockitoBean
    private com.pwb.infra.outbox.api.OutboxWriter outboxWriter;

    @Autowired
    private MailKafkaConsumer consumer;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void resetMocks() {
        org.mockito.Mockito.reset(mailSender);
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
    }

    @Test
    @DisplayName("should_send_email_when_payload_valid")
    void should_send_email_when_payload_valid() {
        AtomicInteger sends = new AtomicInteger(0);
        doAnswer(inv -> {
            sends.incrementAndGet();
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        String payload = createEmailJson("user@example.com", "Subject", "<p>Hi</p>");
        kafkaTemplate.send(TOPIC, "key-1", payload);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(sends.get()).isGreaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("should_throw_mail_payload_exception_when_json_invalid")
    void should_throw_mail_payload_exception_when_json_invalid() {
        kafkaTemplate.send(TOPIC, "key-bad", "{not json}");

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(true).isTrue());
    }

    @Test
    @DisplayName("should_throw_template_exception_when_subject_blank")
    void should_throw_template_exception_when_subject_blank() {
        String payload = createEmailJson("user@example.com", "  ", "<p>Hi</p>");
        kafkaTemplate.send(TOPIC, "key-no-subj", payload);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(true).isTrue());
        verify(mailSender, times(0)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("should_throw_template_exception_when_html_body_blank")
    void should_throw_template_exception_when_html_body_blank() {
        String payload = createEmailJson("user@example.com", "Subject", "");
        kafkaTemplate.send(TOPIC, "key-no-body", payload);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(true).isTrue());
    }

    @Test
    @DisplayName("should_throw_template_exception_when_to_email_blank")
    void should_throw_template_exception_when_to_email_blank() {
        String payload = createEmailJson("  ", "Subject", "<p>Hi</p>");
        kafkaTemplate.send(TOPIC, "key-no-recipient", payload);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(true).isTrue());
    }

    @Test
    @DisplayName("should_use_default_from_when_payload_from_blank")
    void should_use_default_from_when_payload_from_blank() {
        AtomicInteger sends = new AtomicInteger();
        doAnswer(inv -> {
            sends.incrementAndGet();
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        EmailPayload payload = new EmailPayload(
                "welcome",
                "user@example.com",
                UUID.randomUUID(),
                Map.of(),
                "vi",
                "Subject",
                "<p>Hi</p>",
                null,
                null);
        try {
            kafkaTemplate.send(TOPIC, "k-default-from", objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(sends.get()).isGreaterThanOrEqualTo(1));
    }

    private String createEmailJson(String to, String subject, String html) {
        EmailPayload payload = new EmailPayload(
                "welcome",
                to,
                UUID.randomUUID(),
                Map.of(),
                "vi",
                subject,
                html,
                null,
                "noreply@pwb.local");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
