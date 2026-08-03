package com.pwb.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Nested
    @DisplayName("createLocal")
    class CreateLocal {

        @Test
        @DisplayName("should build user with PENDING_VERIFICATION status and trimmed fullName")
        void should_create_local_user_with_pending_status() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed:value"),
                    "  Alice Doe  ",
                    RoleName.USER
            );

            assertThat(user.getUserId()).isNotNull();
            assertThat(user.getEmail().value()).isEqualTo("user@example.com");
            assertThat(user.getFullName()).isEqualTo("Alice Doe");
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(user.getRole()).isEqualTo(RoleName.USER);
            assertThat(user.getOauthProvider()).isEqualTo(OAuthProvider.LOCAL);
            assertThat(user.getOauthId()).isNull();
            assertThat(user.isOAuthUser()).isFalse();
            assertThat(user.getPassword().hash()).isEqualTo("hashed:value");
        }

        @Test
        @DisplayName("should default role to USER when null provided")
        void should_default_role_when_null() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed:value"),
                    "Alice",
                    null
            );

            assertThat(user.getRole()).isEqualTo(RoleName.USER);
        }

        @Test
        @DisplayName("should reject null email")
        void should_reject_null_email() {
            assertThatThrownBy(() -> User.createLocal(
                    null,
                    Password.fromHash("hashed:value"),
                    "Alice",
                    RoleName.USER
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject null or unhashed password")
        void should_reject_invalid_password() {
            assertThatThrownBy(() -> User.createLocal(
                    EmailAddress.of("user@example.com"),
                    null,
                    "Alice",
                    RoleName.USER
            )).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("x"),
                    "Alice",
                    RoleName.USER
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject blank fullName")
        void should_reject_blank_full_name() {
            assertThatThrownBy(() -> User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed:value"),
                    "   ",
                    RoleName.USER
            )).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed:value"),
                    null,
                    RoleName.USER
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject fullName longer than 128 chars")
        void should_reject_too_long_full_name() {
            String longName = "a".repeat(129);

            assertThatThrownBy(() -> User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed:value"),
                    longName,
                    RoleName.USER
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should accept fullName exactly 128 chars")
        void should_accept_max_length_full_name() {
            String exactName = "a".repeat(128);

            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed:value"),
                    exactName,
                    RoleName.USER
            );

            assertThat(user.getFullName()).hasSize(128);
        }
    }

    @Nested
    @DisplayName("createGoogle")
    class CreateGoogle {

        @Test
        @DisplayName("should build google user with provided payload")
        void should_create_google_user() {
            User user = User.createGoogle(
                    EmailAddress.of("google@example.com"),
                    "google-sub-1",
                    "Google User",
                    "https://example.com/avatar.png"
            );

            assertThat(user.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
            assertThat(user.getOauthId()).isEqualTo("google-sub-1");
            assertThat(user.getFullName()).isEqualTo("Google User");
            assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(user.getRole()).isEqualTo(RoleName.USER);
            assertThat(user.isOAuthUser()).isTrue();
        }

        @Test
        @DisplayName("should accept blank fullName as null")
        void should_handle_blank_full_name() {
            User user = User.createGoogle(
                    EmailAddress.of("google@example.com"),
                    "google-sub-1",
                    "  ",
                    null
            );

            assertThat(user.getFullName()).isNull();
            assertThat(user.getAvatarUrl()).isNull();
        }

        @Test
        @DisplayName("should reject null email or blank oauthId")
        void should_reject_invalid_arguments() {
            assertThatThrownBy(() -> User.createGoogle(null, "sub", "name", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> User.createGoogle(EmailAddress.of("a@b.com"), null, "name", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> User.createGoogle(EmailAddress.of("a@b.com"), "  ", "name", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("verifyOtp should mark user active from PENDING_VERIFICATION")
        void should_mark_active_after_verify_otp() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );

            user.verifyOtp();

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("verifyOtp should throw when user BANNED")
        void should_throw_when_banned() {
            User user = User.rehydrate(
                    java.util.UUID.randomUUID(),
                    "user@example.com",
                    "hashed",
                    "Alice",
                    null,
                    null,
                    UserStatus.BANNED,
                    "USER",
                    null,
                    null
            );

            assertThatThrownBy(user::verifyOtp)
                    .isInstanceOf(com.pwb.iam.domain.exception.UserStateConflictException.class);
        }

        @Test
        @DisplayName("verifyOtp should throw when user DELETED")
        void should_throw_when_deleted() {
            User user = User.rehydrate(
                    java.util.UUID.randomUUID(),
                    "user@example.com",
                    "hashed",
                    "Alice",
                    null,
                    null,
                    UserStatus.DELETED,
                    "USER",
                    null,
                    null
            );

            assertThatThrownBy(user::verifyOtp)
                    .isInstanceOf(com.pwb.iam.domain.exception.UserStateConflictException.class);
        }

        @Test
        @DisplayName("markActiveFromRegistration should throw when not pending")
        void should_throw_when_mark_active_not_pending() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );
            user.markActive();

            assertThatThrownBy(user::markActiveFromRegistration)
                    .isInstanceOf(com.pwb.iam.domain.exception.UserStateConflictException.class);
        }

        @Test
        @DisplayName("assignRole should replace role and touch entity")
        void should_assign_role() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );

            user.assignRole(RoleName.ADMIN);

            assertThat(user.getRole()).isEqualTo(RoleName.ADMIN);
            assertThat(user.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("changePassword should update password hash")
        void should_change_password() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("old"),
                    "Alice",
                    RoleName.USER
            );

            user.changePassword(Password.fromHash("new"));

            assertThat(user.getPassword().hash()).isEqualTo("new");
        }

        @Test
        @DisplayName("changePassword should reject unhashed password")
        void should_reject_unhashed_password() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("old"),
                    "Alice",
                    RoleName.USER
            );

            assertThatThrownBy(() -> user.changePassword(Password.fromHash("x")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("linkOAuth should set provider and id")
        void should_link_oauth() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );

            user.linkOAuth(OAuthProvider.GOOGLE, "google-sub-1");

            assertThat(user.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
            assertThat(user.getOauthId()).isEqualTo("google-sub-1");
            assertThat(user.isOAuthUser()).isTrue();
        }

        @Test
        @DisplayName("linkOAuth should reject null provider or blank oauthId")
        void should_reject_invalid_link() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );

            assertThatThrownBy(() -> user.linkOAuth(null, "id"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> user.linkOAuth(OAuthProvider.GOOGLE, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("updateProfile should update fields when provided")
        void should_update_profile() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );

            user.updateProfile("Alice Updated", "+84-12345", "https://example.com/pic.png");

            assertThat(user.getFullName()).isEqualTo("Alice Updated");
            assertThat(user.getPhone()).isEqualTo("+84-12345");
            assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/pic.png");
        }

        @Test
        @DisplayName("updateProfile with null fullName should keep existing")
        void should_keep_full_name_when_null() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );

            user.updateProfile(null, "+84-12345", null);

            assertThat(user.getFullName()).isEqualTo("Alice");
            assertThat(user.getPhone()).isEqualTo("+84-12345");
            assertThat(user.getAvatarUrl()).isNull();
        }

        @Test
        @DisplayName("isOnboardingIncomplete should return false (current behavior)")
        void should_return_false_for_is_onboarding_incomplete() {
            User user = User.createLocal(
                    EmailAddress.of("user@example.com"),
                    Password.fromHash("hashed"),
                    "Alice",
                    RoleName.USER
            );

            assertThat(user.isOnboardingIncomplete()).isFalse();
        }

        @Test
        @DisplayName("rehydrate should preserve all provided fields")
        void should_rehydrate_preserving_fields() {
            UUID userId = UUID.randomUUID();
            User user = User.rehydrate(
                    userId,
                    "user@example.com",
                    "hash",
                    "Name",
                    "avatar.png",
                    "0123",
                    UserStatus.ACTIVE,
                    "PRO",
                    OAuthProvider.LOCAL,
                    null
            );

            assertThat(user.getUserId()).isEqualTo(userId);
            assertThat(user.getEmail().value()).isEqualTo("user@example.com");
            assertThat(user.getFullName()).isEqualTo("Name");
            assertThat(user.getRole()).isEqualTo(RoleName.PRO);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("rehydrate with null password and email should not throw")
        void should_rehydrate_with_nulls() {
            User user = User.rehydrate(
                    UUID.randomUUID(),
                    null,
                    null,
                    "Name",
                    null,
                    null,
                    UserStatus.ACTIVE,
                    "USER",
                    OAuthProvider.GOOGLE,
                    "sub"
            );

            assertThat(user.getEmail()).isNull();
            assertThat(user.getPassword()).isNull();
        }

        @Test
        @DisplayName("rehydrate with unknown roleName should produce null role")
        void should_handle_unknown_role() {
            User user = User.rehydrate(
                    UUID.randomUUID(),
                    "user@example.com",
                    null,
                    "Name",
                    null,
                    null,
                    UserStatus.ACTIVE,
                    "NOT_A_ROLE",
                    OAuthProvider.LOCAL,
                    null
            );

            assertThat(user.getRole()).isNull();
        }
    }
}