package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.consumer.MailTemplateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThymeleafEmailRendererTest {

    /**
     * Every mail is rendered through this one file. The per-template file supplies only a
     * {@code content} fragment, named through the {@code childTemplate} context variable — so the
     * template the caller asked for is a <em>variable</em> here, not the processed template name.
     */
    private static final String LAYOUT = "email/_layout";

    @Mock private SpringTemplateEngine emailTemplateEngine;
    @Mock private MessageSource messageSource;

    private ThymeleafEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ThymeleafEmailRenderer(emailTemplateEngine, messageSource);
        // The renderer only ever uses the overload that carries a default, so a missing translation
        // degrades to a blank slot instead of throwing mid-send. Both the args and the default are
        // nullable because `text(...)` passes null for each in turn.
        lenient().when(messageSource.getMessage(
                        anyString(), nullable(Object[].class), nullable(String.class), any(Locale.class)))
                .thenReturn("subject-line");
    }

    @Test
    @DisplayName("renderHtml should process the shared layout")
    void should_render_html() {
        when(emailTemplateEngine.process(eq(LAYOUT), any(Context.class))).thenReturn("<html/>");

        String html = renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of("code", "123456"), "vi");

        assertThat(html).isEqualTo("<html/>");
    }

    @Test
    @DisplayName("renderHtml should pass domain variables through to the template context")
    void should_pass_variables_into_context() {
        when(emailTemplateEngine.process(eq(LAYOUT), any(Context.class))).thenReturn("<html/>");

        renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of("code", "123456"), "vi");

        // Domain values are applied after the bundle lookups, so a use case passing `code` wins
        // over any same-named bundle entry.
        assertThat(capturedContext().getVariable("code")).isEqualTo("123456");
    }

    @Test
    @DisplayName("renderHtml should wrap TemplateInputException as MailTemplateException")
    void should_wrap_template_input_exception() {
        when(emailTemplateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new TemplateInputException("invalid"));

        assertThatThrownBy(() -> renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of(), "vi"))
                .isInstanceOf(MailTemplateException.class)
                .hasMessageContaining("HTML template input error");
    }

    @Test
    @DisplayName("renderHtml should wrap generic TemplateEngineException as MailTemplateException")
    void should_wrap_generic_template_engine_exception() {
        when(emailTemplateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new TemplateProcessingException("engine fail"));

        assertThatThrownBy(() -> renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of(), "vi"))
                .isInstanceOf(MailTemplateException.class)
                .hasMessageContaining("HTML template processing failed");
    }

    @Test
    @DisplayName("renderText should return empty without touching the engine when no .txt variant exists")
    void should_return_empty_text_when_no_text_template() {
        // No template in this module ships a .txt file, so this is the live path for every mail.
        // Not touching the engine is the point: both resolvers sit on it with the HTML one first,
        // so processing a name with no .txt file silently resolves the .html file instead — which
        // put a full HTML document into the text/plain part and rendered every mail twice.
        String text = renderer.renderText(EmailTemplate.OTP_REGISTER, Map.of(), "vi");

        assertThat(text).isEmpty();
        verify(emailTemplateEngine, never()).process(anyString(), any(Context.class));
    }

    @Test
    @DisplayName("resolveSubject should read the template-prefixed bundle key")
    void should_resolve_subject() {
        String subject = renderer.resolveSubject(EmailTemplate.OTP_REGISTER, "vi");

        assertThat(subject).isEqualTo("subject-line");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(messageSource).getMessage(
                key.capture(), nullable(Object[].class), nullable(String.class), any(Locale.class));
        assertThat(key.getValue()).isEqualTo("email.otp_register.subject");
    }

    @Test
    @DisplayName("should default a null locale to vi")
    void should_default_null_locale() {
        when(emailTemplateEngine.process(eq(LAYOUT), any(Context.class))).thenReturn("<html/>");

        renderer.renderHtml(EmailTemplate.OTP_REGISTER, Map.of(), null);

        assertThat(capturedContext().getLocale()).isEqualTo(Locale.forLanguageTag("vi"));
    }

    @Test
    @DisplayName("should convert the template name to lower-case hyphen format")
    void should_use_hyphenated_template_name() {
        when(emailTemplateEngine.process(eq(LAYOUT), any(Context.class))).thenReturn("<html/>");

        renderer.renderHtml(EmailTemplate.WELCOME_GOOGLE, Map.of(), "vi");

        // WELCOME_GOOGLE -> email/welcome-google. Template files must follow this exactly; a
        // mismatch surfaces only at send time, as an unresolved fragment.
        assertThat(capturedContext().getVariable("childTemplate")).isEqualTo("email/welcome-google");
    }

    private Context capturedContext() {
        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(emailTemplateEngine).process(eq(LAYOUT), captor.capture());
        return captor.getValue();
    }
}
