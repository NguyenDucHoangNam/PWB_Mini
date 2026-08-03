package com.pwb.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    @DisplayName("should accept and normalize valid email to lower case")
    void should_normalize_to_lower_case() {
        EmailAddress address = EmailAddress.of("Foo.Bar@Example.COM");

        assertThat(address.value()).isEqualTo("foo.bar@example.com");
    }

    @Test
    @DisplayName("should trim leading and trailing whitespace")
    void should_trim_whitespace() {
        EmailAddress address = EmailAddress.of("  user@example.com  ");

        assertThat(address.value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("should expose value via toString")
    void should_return_value_in_to_string() {
        EmailAddress address = EmailAddress.of("user@example.com");

        assertThat(address).hasToString("user@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plainaddress",
            "@no-local-part.com",
            "missing-at-sign.com",
            "trailing-dot.@example.com",
            ".leading-dot@example.com",
            "double..dot@example.com",
            "no-tld@example",
            "spaces in@example.com",
            ""
    })
    @DisplayName("should reject malformed email formats")
    void should_reject_invalid_email(String raw) {
        assertThatThrownBy(() -> EmailAddress.of(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email is not valid");
    }

    @Test
    @DisplayName("should reject null email")
    void should_reject_null_email() {
        assertThatThrownBy(() -> EmailAddress.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user@example.com",
            "first.last@example.co",
            "user+tag@example.io",
            "123@example.com",
            "USER@EXAMPLE.COM"
    })
    @DisplayName("should accept well-formed emails")
    void should_accept_valid_emails(String raw) {
        EmailAddress address = EmailAddress.of(raw);

        assertThat(address.value()).isNotBlank();
    }

    @Test
    @DisplayName("two EmailAddress with same normalized value should be equal")
    void should_equality_based_on_value() {
        EmailAddress first = EmailAddress.of("User@Example.com");
        EmailAddress second = EmailAddress.of("user@example.com ");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}