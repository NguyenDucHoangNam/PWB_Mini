package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.PasswordResetPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenServiceImplTest {

    private PasswordResetTokenServiceImpl service;
    private PasswordResetPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PasswordResetPolicy(
                "this-is-a-test-secret-must-be-at-least-32-bytes-long-1234",
                30L, 60L, "http://localhost:3000", "/reset-password");
        service = new PasswordResetTokenServiceImpl(policy);
    }

    @Test
    @DisplayName("generateSignedToken should return token with separator and signature")
    void should_generate_signed_token() {
        String token = service.generateSignedToken();

        assertThat(token).contains(".");
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isNotBlank();
        assertThat(parts[1]).isNotBlank();
    }

    @Test
    @DisplayName("extractRawToken should return part before separator")
    void should_extract_raw_token() {
        String token = service.generateSignedToken();

        String raw = service.extractRawToken(token);

        assertThat(raw).isNotBlank();
        assertThat(token).startsWith(raw + ".");
    }

    @Test
    @DisplayName("extractRawToken should return null for invalid input")
    void should_return_null_for_invalid_input() {
        assertThat(service.extractRawToken(null)).isNull();
        assertThat(service.extractRawToken("")).isNull();
        assertThat(service.extractRawToken("no-separator")).isNull();
    }

    @Test
    @DisplayName("verifySignature should return true for valid signed token")
    void should_verify_signature() {
        String token = service.generateSignedToken();

        assertThat(service.verifySignature(token)).isTrue();
    }

    @Test
    @DisplayName("verifySignature should return false for tampered signature")
    void should_reject_tampered_token() {
        String token = service.generateSignedToken();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + "AAAA";

        assertThat(service.verifySignature(tampered)).isFalse();
    }

    @Test
    @DisplayName("verifySignature should return false for invalid input")
    void should_reject_invalid_input() {
        assertThat(service.verifySignature(null)).isFalse();
        assertThat(service.verifySignature("")).isFalse();
        assertThat(service.verifySignature("no-separator")).isFalse();
    }

    @Test
    @DisplayName("hashForStorage should produce deterministic SHA-256 hex")
    void should_hash_deterministically() {
        String hash1 = service.hashForStorage("raw-token");
        String hash2 = service.hashForStorage("raw-token");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("buildResetLink should combine frontendUrl, resetPath and token query")
    void should_build_reset_link() {
        String link = service.buildResetLink("abc");

        assertThat(link).isEqualTo("http://localhost:3000/reset-password?token=abc");
    }

    @Test
    @DisplayName("buildResetLink should trim trailing slash from frontendUrl")
    void should_trim_trailing_slash() {
        PasswordResetPolicy trailing = new PasswordResetPolicy(
                "this-is-a-test-secret-must-be-at-least-32-bytes-long-1234",
                30L, 60L, "http://localhost:3000/", "/reset-password");
        PasswordResetTokenServiceImpl local = new PasswordResetTokenServiceImpl(trailing);

        assertThat(local.buildResetLink("abc")).isEqualTo("http://localhost:3000/reset-password?token=abc");
    }

    @Test
    @DisplayName("buildResetLink should add leading slash to resetPath if missing")
    void should_add_leading_slash() {
        PasswordResetPolicy noSlash = new PasswordResetPolicy(
                "this-is-a-test-secret-must-be-at-least-32-bytes-long-1234",
                30L, 60L, "http://localhost:3000", "reset-password");
        PasswordResetTokenServiceImpl local = new PasswordResetTokenServiceImpl(noSlash);

        assertThat(local.buildResetLink("abc")).isEqualTo("http://localhost:3000/reset-password?token=abc");
    }
}