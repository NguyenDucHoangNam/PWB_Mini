package com.pwb.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class User extends BaseEntity {

    private final UUID userId;
    private String username;
    private EmailAddress email;
    private Password password;
    private String fullName;
    private String avatarUrl;
    private String phone;
    private UserStatus status;
    private RoleName role;
    private OAuthProvider oauthProvider;
    private String oauthId;
    private boolean provisionalUsername;

    private User(
            UUID userId,
            String username,
            EmailAddress email,
            Password password,
            String fullName,
            String avatarUrl,
            String phone,
            UserStatus status,
            RoleName role,
            OAuthProvider oauthProvider,
            String oauthId,
            boolean provisionalUsername
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
        this.provisionalUsername = provisionalUsername;
    }

    public static User createLocal(
            String username,
            EmailAddress email,
            Password password,
            String fullName,
            RoleName role
    ) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (password == null || !password.isHashed()) {
            throw new IllegalArgumentException("password must be hashed");
        }
        if (role == null) {
            role = RoleName.USER;
        }
        return new User(
                UUID.randomUUID(),
                username,
                email,
                password,
                fullName,
                null,
                null,
                UserStatus.PENDING_VERIFICATION,
                role,
                OAuthProvider.LOCAL,
                null,
                true
        );
    }

    public static User createGoogle(
            String username,
            EmailAddress email,
            String oauthId,
            String fullName,
            String avatarUrl
    ) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (oauthId == null || oauthId.isBlank()) {
            throw new IllegalArgumentException("oauthId must not be blank");
        }
        return new User(
                UUID.randomUUID(),
                username,
                email,
                null,
                fullName,
                avatarUrl,
                null,
                UserStatus.PENDING_VERIFICATION,
                RoleName.USER,
                OAuthProvider.GOOGLE,
                oauthId,
                true
        );
    }

    public static User rehydrate(
            UUID userId,
            String username,
            EmailAddress email,
            String passwordHash,
            String fullName,
            String avatarUrl,
            String phone,
            UserStatus status,
            RoleName role,
            OAuthProvider oauthProvider,
            String oauthId,
            boolean provisionalUsername
    ) {
        Password password = (passwordHash == null || passwordHash.isBlank())
                ? null
                : Password.fromHash(passwordHash);
        return new User(
                userId,
                username,
                email,
                password,
                fullName,
                avatarUrl,
                phone,
                status,
                role,
                oauthProvider,
                oauthId,
                provisionalUsername
        );
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public EmailAddress getEmail() {
        return email;
    }

    public Password getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public RoleName getRole() {
        return role;
    }

    public OAuthProvider getOauthProvider() {
        return oauthProvider;
    }

    public String getOauthId() {
        return oauthId;
    }

    public boolean isProvisionalUsername() {
        return provisionalUsername;
    }

    public void markActive() {
        this.status = UserStatus.ACTIVE;
        touch();
    }

    public void assignRole(RoleName newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        this.role = newRole;
        touch();
    }

    public void changePassword(Password newPassword) {
        if (newPassword == null || !newPassword.isHashed()) {
            throw new IllegalArgumentException("password must be hashed");
        }
        this.password = newPassword;
        touch();
    }

    public void changeUsername(String username) {
        this.username = username;
        this.provisionalUsername = false;
        touch();
    }

    public void updateProfile(String fullName, String phone, String avatarUrl) {
        this.fullName = fullName;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        touch();
    }

    public void changeAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        touch();
    }

    public void changeFullName(String fullName) {
        this.fullName = fullName;
        touch();
    }

    public void linkOAuth(OAuthProvider provider, String oauthId) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (oauthId == null || oauthId.isBlank()) {
            throw new IllegalArgumentException("oauthId must not be blank");
        }
        this.oauthProvider = provider;
        this.oauthId = oauthId;
        touch();
    }
}
