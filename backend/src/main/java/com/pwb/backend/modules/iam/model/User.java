package com.pwb.backend.modules.iam.model;

import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.UUID;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int USERNAME_LOCAL_SUFFIX_BOUND = 1_000_000;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 16)
    private OauthProvider oauthProvider;

    @Column(name = "oauth_id", length = 255)
    private String oauthId;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public User(UUID id,
                String email,
                String passwordHash,
                String fullName,
                UserStatus status,
                Role role,
                OauthProvider oauthProvider,
                String oauthId,
                String avatarUrl) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = status;
        this.role = role;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
        this.avatarUrl = avatarUrl;
    }

    public static User newPending(String email, String passwordHash, String fullName, Role role) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.status = UserStatus.PENDING_VERIFICATION;
        user.role = role;
        user.oauthProvider = OauthProvider.LOCAL;
        return user;
    }

    public static User newOAuthActive(String email,
                                      String fullName,
                                      String oauthId,
                                      String avatarUrl,
                                      Role role) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email;
        user.passwordHash = null;
        user.fullName = fullName;
        user.status = UserStatus.ACTIVE;
        user.role = role;
        user.oauthProvider = OauthProvider.GOOGLE;
        user.oauthId = oauthId;
        user.avatarUrl = avatarUrl;
        return user;
    }

    public void linkOAuth(String oauthId, String fullName, String avatarUrl) {
        this.oauthProvider = OauthProvider.GOOGLE;
        this.oauthId = oauthId;
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName;
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            this.avatarUrl = avatarUrl;
        }
    }

    public void activateFromOtp() {
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = Instant.now();
    }

    public void markLoggedIn(Instant when) {
        this.lastLoginAt = when;
    }

    public boolean isLocal() {
        return oauthProvider == OauthProvider.LOCAL;
    }

    public static String generateUsernamePrefix(String email) {
        if (email == null) {
            return "user";
        }
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < local.length() && sb.length() < 20; i++) {
            char c = local.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? "user" : sb.toString();
    }

    public static String generateUsernameSuffix() {
        return Integer.toString(SECURE_RANDOM.nextInt(USERNAME_LOCAL_SUFFIX_BOUND));
    }
}
