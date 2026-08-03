package com.pwb.iam.infrastructure.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureOtpGeneratorTest {

    private final SecureOtpGenerator generator = new SecureOtpGenerator();

    @Test
    @DisplayName("generate should produce 6-digit numeric string")
    void should_generate_six_digit_code() {
        String code = generator.generate();

        assertThat(code).hasSize(6);
        assertThat(code).matches("\\d{6}");
    }

    @Test
    @DisplayName("generate(int) should respect requested length")
    void should_generate_with_custom_length() {
        assertThat(generator.generate(8)).hasSize(8).matches("\\d{8}");
        assertThat(generator.generate(4)).hasSize(4).matches("\\d{4}");
    }

    @Test
    @DisplayName("hash should produce deterministic SHA-256 hash")
    void should_hash_deterministically() {
        String hash1 = generator.hash("123456");
        String hash2 = generator.hash("123456");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotBlank();
        assertThat(hash1).isNotEqualTo("123456");
    }

    @Test
    @DisplayName("matches should return true when hash matches")
    void should_match_for_correct_code() {
        String hash = generator.hash("654321");

        assertThat(generator.matches("654321", hash)).isTrue();
    }

    @Test
    @DisplayName("matches should return false for null or mismatched input")
    void should_not_match_for_null_or_wrong() {
        String hash = generator.hash("111111");

        assertThat(generator.matches(null, hash)).isFalse();
        assertThat(generator.matches("111111", null)).isFalse();
        assertThat(generator.matches("222222", hash)).isFalse();
    }
}