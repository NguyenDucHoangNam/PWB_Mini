package com.pwb.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordTest {

    @Test
    @DisplayName("should keep hash string")
    void should_keep_hash() {
        Password password = Password.fromHash("$2a$10$abcd");

        assertThat(password.hash()).isEqualTo("$2a$10$abcd");
    }

    @Test
    @DisplayName("should reject null or blank hash")
    void should_reject_null_blank_hash() {
        assertThatThrownBy(() -> Password.fromHash(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Password.fromHash(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Password.fromHash("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should report isHashed true when hash non-blank")
    void should_report_hashed_true() {
        assertThat(Password.fromHash("hash").isHashed()).isTrue();
    }

    @Test
    @DisplayName("isHashed should hold for every constructed instance")
    void should_report_hashed_false_for_empty() {
        // There is no such thing as an unhashed Password: a blank hash is rejected at construction,
        // so isHashed() is true by definition and exists only so callers can write
        // `p != null && p.isHashed()`. This test used to assert it could be false, which no value
        // reachable through the constructor can produce.
        assertThat(Password.fromHash("x").isHashed()).isTrue();
        assertThat(Password.fromHash("dummy").isHashed()).isTrue();
    }

    @Test
    @DisplayName("should be equal when hash matches")
    void should_be_equal_by_hash() {
        Password first = Password.fromHash("hashA");
        Password second = Password.fromHash("hashA");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}