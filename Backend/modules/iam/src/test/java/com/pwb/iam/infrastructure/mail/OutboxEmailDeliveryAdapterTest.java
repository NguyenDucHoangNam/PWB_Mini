package com.pwb.iam.infrastructure.mail;

import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.infra.mail.api.EmailEventRequested;
import com.pwb.infra.mail.api.EmailPayload;
import com.pwb.infra.mail.api.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEmailDeliveryAdapterTest {

    @Mock private ThymeleafEmailRenderer renderer;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private OutboxEmailDeliveryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OutboxEmailDeliveryAdapter(renderer, applicationEventPublisher);
        ReflectionTestUtils.setField(adapter, "defaultFrom", "noreply@pwb.local");
        when(renderer.resolveSubject(any(), any())).thenReturn("subject");
        when(renderer.renderHtml(any(), any(), any())).thenReturn("<html/>");
        when(renderer.renderText(any(), any(), any())).thenReturn("text");
    }

    @Test
    @DisplayName("should publish EmailEventRequested with rendered payload")
    void should_publish_event_with_payload() {
        UUID userId = UUID.randomUUID();
        Map<String, String> variables = Map.of("code", "123456");

        adapter.enqueue(new EmailEnqueueCommand(userId, "user@example.com", EmailTemplate.OTP_REGISTER, variables, null));

        ArgumentCaptor<EmailEventRequested> captor = ArgumentCaptor.forClass(EmailEventRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        EmailPayload payload = captor.getValue().payload();
        assertThat(payload.template()).isEqualTo(EmailTemplate.OTP_REGISTER.name());
        assertThat(payload.toEmail()).isEqualTo("user@example.com");
        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.variables()).containsEntry("code", "123456");
        assertThat(payload.locale()).isEqualTo("vi");
        assertThat(payload.from()).isEqualTo("noreply@pwb.local");
        assertThat(payload.htmlBody()).isEqualTo("<html/>");
        assertThat(payload.textBody()).isEqualTo("text");
    }

    @Test
    @DisplayName("should use provided locale when not blank")
    void should_use_provided_locale() {
        adapter.enqueue(new EmailEnqueueCommand(UUID.randomUUID(), "user@example.com",
                EmailTemplate.WELCOME_GOOGLE, Map.of(), "en"));

        ArgumentCaptor<EmailEventRequested> captor = ArgumentCaptor.forClass(EmailEventRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().locale()).isEqualTo("en");
    }

    @Test
    @DisplayName("should default null variables to empty map")
    void should_default_null_variables() {
        adapter.enqueue(new EmailEnqueueCommand(UUID.randomUUID(), "user@example.com",
                EmailTemplate.OTP_REGISTER, null, "vi"));

        ArgumentCaptor<EmailEventRequested> captor = ArgumentCaptor.forClass(EmailEventRequested.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().variables()).isEmpty();
    }
}