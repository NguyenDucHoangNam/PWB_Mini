package com.pwb.iam.infrastructure.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SpringPasswordHasherTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SpringPasswordHasher hasher = new SpringPasswordHasher(encoder);

    @Test
    @DisplayName("hash should delegate to encoder and return non-blank")
    void should_hash_password() {
        String hashed = hasher.hash("secret");

        assertThat(hashed).isNotBlank();
        assertThat(hashed).startsWith("$2");
    }

    @Test
    @DisplayName("matches should return true for correct password")
    void should_match_password() {
        String hashed = hasher.hash("secret");

        assertThat(hasher.matches("secret", hashed)).isTrue();
    }

    @Test
    @DisplayName("matches should return false for wrong password")
    void should_not_match_wrong_password() {
        String hashed = hasher.hash("secret");

        assertThat(hasher.matches("wrong", hashed)).isFalse();
    }

    @Test
    @DisplayName("matches should return false for null or blank hash")
    void should_handle_null_inputs() {
        assertThat(hasher.matches("secret", null)).isFalse();
        assertThat(hasher.matches("secret", "")).isFalse();
        assertThat(hasher.matches("secret", "   ")).isFalse();
        assertThat(hasher.matches(null, "hash")).isFalse();
    }
}