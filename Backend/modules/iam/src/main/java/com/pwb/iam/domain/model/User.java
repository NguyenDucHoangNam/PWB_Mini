package com.pwb.iam.domain.model;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.UserStateConflictException;
import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;

public final class User extends DomainBaseEntity {

    private static final int FULL_NAME_MAX_LENGTH = 128;

    private final UUID userId;
    private EmailAddress email;
    private Password password;
    private String fullName;
    private String avatarUrl;
    private String phone;
    private UserStatus status;
    private RoleName role;
    private OAuthProvider oauthProvider;
    private String oauthId;
    private String banReason;
    private Instant bannedAt;
    private UUID bannedBy;

    private User(
            UUID userId,
            EmailAddress email,
            Password password,
            String fullName,
            String avatarUrl,
            String phone,
            UserStatus status,
            RoleName role,
            OAuthProvider oauthProvider,
            String oauthId,
            String banReason,
            Instant bannedAt,
            UUID bannedBy
    ) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.status = status;
        this.role = role;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
        this.banReason = banReason;
        this.bannedAt = bannedAt;
        this.bannedBy = bannedBy;
    }

    public static User createLocal(
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
        String normalizedFullName = normalizeFullName(fullName);
        if (role == null) {
            role = RoleName.USER;
        }
        return new User(
                UUID.randomUUID(),
                email,
                password,
                normalizedFullName,
                null,
                null,
                UserStatus.PENDING_VERIFICATION,
                role,
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null
        );
    }

    public static User createGoogle(
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
        String normalizedFullName = fullName == null || fullName.isBlank() ? null : normalizeFullName(fullName);
        return new User(
                UUID.randomUUID(),
                email,
                null,
                normalizedFullName,
                avatarUrl,
                null,
                UserStatus.PENDING_VERIFICATION,
                RoleName.USER,
                OAuthProvider.GOOGLE,
                oauthId,
                null,
                null,
                null
        );
    }

    public static User rehydrate(
            UUID userId,
            String email,
            String passwordHash,
            String fullName,
            String avatarUrl,
            String phone,
            UserStatus status,
            String roleName,
            OAuthProvider oauthProvider,
            String oauthId,
            String banReason,
            Instant bannedAt,
            UUID bannedBy
    ) {
        EmailAddress emailAddress = (email == null || email.isBlank()) ? null : EmailAddress.of(email);
        Password password = (passwordHash == null || passwordHash.isBlank())
                ? null
                : Password.fromHash(passwordHash);
        RoleName role = null;
        if (roleName != null && !roleName.isBlank()) {
            try {
                role = RoleName.valueOf(roleName);
            } catch (IllegalArgumentException ex) {
                role = null;
            }
        }
        return new User(
                userId,
                emailAddress,
                password,
                fullName,
                avatarUrl,
                phone,
                status == null ? UserStatus.PENDING_VERIFICATION : status,
                role,
                oauthProvider,
                oauthId,
                banReason,
                bannedAt,
                bannedBy
        );
    }

    public UUID getUserId() {
        return userId;
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

    public boolean isOAuthUser() {
        return oauthProvider != null && oauthProvider != OAuthProvider.LOCAL;
    }

    public boolean isBlocked() {
        return status == UserStatus.BANNED || status == UserStatus.DELETED;
    }

    public void markActive() {
        this.status = UserStatus.ACTIVE;
        touch();
    }

    public void verifyOtp() {
        if (isBlocked()) {
            throw new UserStateConflictException(IamErrorCode.ACCOUNT_INACTIVE);
        }
        if (this.status == UserStatus.PENDING_VERIFICATION) {
            this.status = UserStatus.ACTIVE;
            touch();
        }
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

    public void changeAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        touch();
    }

    public void changeFullName(String fullName) {
        this.fullName = normalizeFullName(fullName);
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

    public void ban(String reason, UUID adminId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("ban reason must not be blank");
        }
        if (this.status == UserStatus.BANNED) {
            throw new UserStateConflictException(IamErrorCode.ADMIN_USER_ALREADY_BANNED);
        }
        this.status = UserStatus.BANNED;
        this.banReason = reason.trim();
        this.bannedAt = Instant.now();
        this.bannedBy = adminId;
        touch();
    }

    public void unban() {
        if (this.status != UserStatus.BANNED) {
            throw new UserStateConflictException(IamErrorCode.ADMIN_USER_NOT_BANNED);
        }
        this.status = UserStatus.ACTIVE;
        this.banReason = null;
        this.bannedAt = null;
        this.bannedBy = null;
        touch();
    }

    public void markPendingDeletion() {
        if (this.status == UserStatus.PENDING_DELETION) {
            throw new UserStateConflictException(IamErrorCode.ADMIN_USER_ALREADY_PENDING_DELETION);
        }
        if (this.status == UserStatus.DELETED) {
            throw new UserStateConflictException(IamErrorCode.ACCOUNT_INACTIVE);
        }
        this.status = UserStatus.PENDING_DELETION;
        touch();
    }

    public String getBanReason() {
        return banReason;
    }

    public Instant getBannedAt() {
        return bannedAt;
    }

    public UUID getBannedBy() {
        return bannedBy;
    }

    private static String normalizeFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        String trimmed = fullName.trim();
        if (trimmed.length() > FULL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("fullName must not exceed " + FULL_NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
