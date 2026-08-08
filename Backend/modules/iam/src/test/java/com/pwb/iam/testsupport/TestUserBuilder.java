package com.pwb.iam.testsupport;

import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;

import java.util.UUID;

public final class TestUserBuilder {

    private static final String DEFAULT_HASH = "hashed:Pass1234!@#";

    private TestUserBuilder() {
    }

    public static User localActive() {
        return User.rehydrate(
                UUID.randomUUID(),
                "active@example.com",
                DEFAULT_HASH,
                "Active User",
                null,
                null,
                UserStatus.ACTIVE,
                RoleName.USER.name(),
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static User localPending() {
        return User.rehydrate(
                UUID.randomUUID(),
                "pending@example.com",
                DEFAULT_HASH,
                "Pending User",
                null,
                null,
                UserStatus.PENDING_VERIFICATION,
                RoleName.USER.name(),
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static User googleActive() {
        return User.rehydrate(
                UUID.randomUUID(),
                "google@example.com",
                null,
                "Google User",
                "https://example.com/avatar.png",
                null,
                UserStatus.ACTIVE,
                RoleName.USER.name(),
                OAuthProvider.GOOGLE,
                "google-sub-123",
                null,
                null,
                null
        );
    }

    public static User banned() {
        return User.rehydrate(
                UUID.randomUUID(),
                "banned@example.com",
                DEFAULT_HASH,
                "Banned User",
                null,
                null,
                UserStatus.BANNED,
                RoleName.USER.name(),
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static User deleted() {
        return User.rehydrate(
                UUID.randomUUID(),
                "deleted@example.com",
                DEFAULT_HASH,
                "Deleted User",
                null,
                null,
                UserStatus.DELETED,
                RoleName.USER.name(),
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static User withEmail(String email) {
        return User.rehydrate(
                UUID.randomUUID(),
                email,
                DEFAULT_HASH,
                "User " + email,
                null,
                null,
                UserStatus.ACTIVE,
                RoleName.USER.name(),
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static User withUserId(UUID userId) {
        return User.rehydrate(
                userId,
                "user-" + userId + "@example.com",
                DEFAULT_HASH,
                "User",
                null,
                null,
                UserStatus.ACTIVE,
                RoleName.USER.name(),
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static User withPasswordHash(String passwordHash) {
        return User.rehydrate(
                UUID.randomUUID(),
                "withpass@example.com",
                passwordHash,
                "With Password User",
                null,
                null,
                UserStatus.ACTIVE,
                RoleName.USER.name(),
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static Role defaultRole() {
        return Role.create(RoleName.USER, "Default user role");
    }

    public static EmailAddress email() {
        return EmailAddress.of("user@example.com");
    }

    public static Password password() {
        return Password.fromHash(DEFAULT_HASH);
    }
}
