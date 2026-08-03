package com.pwb.web.message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MessageResolver — i18n message lookup")
class MessageResolverTest {

    private MessageSource messageSource;
    private MessageResolver resolver;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        resolver = new MessageResolver(messageSource);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("should_resolve_message_via_message_source")
    void should_resolve_message_via_message_source() {
        when(messageSource.getMessage(eq("greeting"), any(), eq("greeting"), any(Locale.class)))
                .thenReturn("Hello");

        String resolved = resolver.get("greeting");

        assertThat(resolved).isEqualTo("Hello");
    }

    @Test
    @DisplayName("should_pass_arguments_to_message_source")
    void should_pass_arguments_to_message_source() {
        when(messageSource.getMessage(eq("welcome"), any(), eq("welcome"), any(Locale.class)))
                .thenReturn("Welcome John");

        String resolved = resolver.get("welcome", "John");

        assertThat(resolved).isEqualTo("Welcome John");
    }

    @Test
    @DisplayName("should_use_key_as_default_when_message_source_falls_back")
    void should_use_key_as_default_when_message_source_falls_back() {
        when(messageSource.getMessage(eq("missing.key"), any(), anyString(), any(Locale.class)))
                .thenReturn("missing.key");

        String resolved = resolver.get("missing.key");

        assertThat(resolved).isEqualTo("missing.key");
    }

    @Test
    @DisplayName("should_use_default_message_in_getOrDefault_when_message_source_fails")
    void should_use_default_message_in_getOrDefault_when_message_source_fails() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenThrow(new RuntimeException("source broken"));

        String resolved = resolver.getOrDefault("any.key", "Default message");

        assertThat(resolved).isEqualTo("Default message");
    }

    @Test
    @DisplayName("should_use_default_message_in_getOrDefault_when_resolved_successfully")
    void should_use_default_message_in_getOrDefault_when_resolved_successfully() {
        when(messageSource.getMessage(eq("greeting"), any(), anyString(), any(Locale.class)))
                .thenReturn("Hello world");

        String resolved = resolver.getOrDefault("greeting", "Default");

        assertThat(resolved).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("should_use_default_locale_when_locale_context_unset")
    void should_use_default_locale_when_locale_context_unset() {
        LocaleContextHolder.resetLocaleContext();
        when(messageSource.getMessage(eq("greeting"), any(), eq("greeting"), any(Locale.class)))
                .thenReturn("Hello-default");

        String resolved = resolver.get("greeting");

        assertThat(resolved).isEqualTo("Hello-default");
    }
}