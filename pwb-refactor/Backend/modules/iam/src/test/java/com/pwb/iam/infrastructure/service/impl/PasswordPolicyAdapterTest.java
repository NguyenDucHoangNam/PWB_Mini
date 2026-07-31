package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.infrastructure.config.PasswordPolicyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordPolicyAdapterTest {

    private PasswordPolicyProperties properties;
    private MessageSource messageSource;
    private PasswordPolicyAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new PasswordPolicyProperties();
        properties.setMinLength(12);
        properties.setMaxLength(128);
        messageSource = mock(MessageSource.class);
        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messageSource.getMessage(any(), any(), any(String.class), any(Locale.class))).thenAnswer(inv -> inv.getArgument(2));
        adapter = new PasswordPolicyAdapter(properties, messageSource);
    }

    @Test
    @DisplayName("should return ok for valid strong password")
    void should_accept_valid_password() {
        PasswordPolicyResult result = adapter.validate("StrongP@ss123!");

        assertThat(result.valid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    @DisplayName("should report TOO_SHORT for password below min length")
    void should_report_too_short() {
        PasswordPolicyResult result = adapter.validate("Aa1!short");

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).isNotEmpty();
    }

    @Test
    @DisplayName("should report TOO_LONG for password above max length")
    void should_report_too_long() {
        String tooLong = "A1!" + "a".repeat(130);

        PasswordPolicyResult result = adapter.validate(tooLong);

        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("should report MISSING_UPPER, MISSING_LOWER, MISSING_DIGIT, MISSING_SPECIAL when applicable")
    void should_report_multiple_violations() {
        PasswordPolicyResult result = adapter.validate("alllowercase");

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("should return failure with NULL_PASSWORD key for null input")
    void should_report_null_password() {
        PasswordPolicyResult result = adapter.validate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.messages()).isNotEmpty();
    }

    @Test
    @DisplayName("should report CONTAINS_WHITESPACE for password with space")
    void should_report_whitespace() {
        PasswordPolicyResult result = adapter.validate("Strong P@ss123!");

        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("should report blank password as failure")
    void should_report_blank_password() {
        PasswordPolicyResult result = adapter.validate("   ");

        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("should pass min/max length args to message source")
    void should_pass_min_max_args() {
        when(messageSource.getMessage(eq("password.violation.too_short"), any(), any(Locale.class)))
                .thenReturn("too short");

        PasswordPolicyResult result = adapter.validate("Aa1!short");

        assertThat(result.messages()).contains("too short");
    }
}