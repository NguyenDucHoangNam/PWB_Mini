package com.pwb.iam.core.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class User extends BaseEntity {

    private final UUID userId;
    private String username;
    private EmailAddress email;
    private Password password;
    private String fullName;
    private String avatarUrl;
    private String phone;
    private UserStatus status;
    private Role role;
    private OAuthProvider oauthProvider;
    private String oauthId;
    private Instant deletionRequestedAt;

    private User(
            UUID userId,
            String username,
            EmailAddress email,
            Password password,
            String fullName,
            String avatarUrl,
            String phone,
            UserStatus status,
            Role role,
            OAuthProvider oauthProvider,
            String oauthId,
            Instant deletionRequestedAt
    ) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.status = status;
        this.role = role;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
        this.deletionRequestedAt = deletionRequestedAt;
    }

    public static User createLocal(
            String username,
            EmailAddress email,
            Password password,
            String fullName
    ) {
        return new User(
                UUID.randomUUID(),
                username,
                email,
                password,
                fullName,
                null,
                null,
                UserStatus.PENDING_VERIFICATION,
                Role.defaultUserRole(),
                OAuthProvider.LOCAL,
                null,
                null
        );
    }

    public static User createGoogle(
            String username,
            EmailAddress email,
            String oauthId,
            String fullName,
            String avatarUrl
    ) {
        return new User(
                UUID.randomUUID(),
                username,
                email,
                null,
                fullName,
                avatarUrl,
                null,
                UserStatus.PENDING_VERIFICATION,
                Role.defaultUserRole(),
                OAuthProvider.GOOGLE,
                oauthId,
                null
        );
    }

    public void markActive() {
        this.status = UserStatus.ACTIVE;
        touch();
    }

    public void markPendingDeletion() {
        this.status = UserStatus.PENDING_DELETION;
        this.deletionRequestedAt = Instant.now();
        touch();
    }

    public void markBanned() {
        this.status = UserStatus.BANNED;
        touch();
    }

    public void markDeleted() {
        this.status = UserStatus.DELETED;
        touch();
    }

    public void changeEmail(EmailAddress newEmail) {
        this.email = newEmail;
        touch();
    }

    public void changePassword(Password newPassword) {
        this.password = newPassword;
        touch();
    }

    public void assignRole(Role newRole) {
        this.role = newRole;
        touch();
    }

    public void updateProfile(String fullName, String phone, String avatarUrl) {
        this.fullName = fullName;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        touch();
    }
}
