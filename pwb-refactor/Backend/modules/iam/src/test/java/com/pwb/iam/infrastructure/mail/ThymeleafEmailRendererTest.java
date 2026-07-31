package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.consumer.MailTemplateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThymeleafEmailRendererTest {

    @Mock private SpringTemplateEngine emailTemplateEngine;
    @Mock private MessageSource messageSource;

    private ThymeleafEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ThymeleafEmailRenderer(emailTemplateEngine, messageSource);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("subject-line");
    }

    @Test
    @DisplayName("renderHtml should delegate to template engine with content selector")
    void should_render_html() {
        when(emailTemplateEngine.process(eq("email/otp-register"), anySet(), any(Context.class))).thenReturn("<html/>");

        String html = renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of("code", "123456"), "vi");

        assertThat(html).isEqualTo("<html/>");
    }

    @Test
    @DisplayName("renderHtml should wrap TemplateInputException as MailTemplateException")
    void should_wrap_template_input_exception() {
        when(emailTemplateEngine.process(anyString(), anySet(), any(Context.class)))
                .thenThrow(new TemplateInputException("invalid"));

        assertThatThrownBy(() -> renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of(), "vi"))
                .isInstanceOf(MailTemplateException.class)
                .hasMessageContaining("HTML template input error");
    }

    @Test
    @DisplayName("renderHtml should wrap generic TemplateEngineException as MailTemplateException")
    void should_wrap_generic_template_engine_exception() {
        when(emailTemplateEngine.process(anyString(), anySet(), any(Context.class)))
                .thenThrow(new org.thymeleaf.exceptions.TemplateProcessingException("engine fail"));

        assertThatThrownBy(() -> renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of(), "vi"))
                .isInstanceOf(MailTemplateException.class)
                .hasMessageContaining("HTML template processing failed");
    }

    @Test
    @DisplayName("renderText should return empty string when TemplateEngineException occurs")
    void should_return_empty_text_when_template_engine_error() {
        when(emailTemplateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new org.thymeleaf.exceptions.TemplateProcessingException("not found"));

        String text = renderer.renderText(EmailTemplate.OTP_REGISTER, Map.of(), "vi");

        assertThat(text).isEmpty();
    }

    @Test
    @DisplayName("renderText should return rendered text on success")
    void should_return_rendered_text() {
        when(emailTemplateEngine.process(anyString(), any(Context.class))).thenReturn("text body");

        String text = renderer.renderText(EmailTemplate.OTP_REGISTER, Map.of(), "vi");

        assertThat(text).isEqualTo("text body");
    }

    @Test
    @DisplayName("resolveSubject should call messageSource with template-prefixed key")
    void should_resolve_subject() {
        String subject = renderer.resolveSubject(EmailTemplate.OTP_REGISTER, "vi");

        assertThat(subject).isEqualTo("subject-line");
    }

    @Test
    @DisplayName("should default null locale to vi")
    void should_default_null_locale() {
        when(emailTemplateEngine.process(eq("email/otp-register"), anySet(), any(Context.class))).thenReturn("<html/>");

        String html = renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of(), null);

        assertThat(html).isNotNull();
    }

    @Test
    @DisplayName("should convert template name to lower-case hyphen format")
    void should_use_hyphenated_template_name() {
        when(emailTemplateEngine.process(eq("email/welcome-google"), anySet(), any(Context.class))).thenReturn("<html/>");

        String html = renderer.renderHtml(EmailTemplate.WELCOME_GOOGLE, Map.of(), "vi");

        assertThat(html).isNotNull();
    }
}