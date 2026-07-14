package com.pwb.backend.modules.iam.model;

import com.pwb.backend.common.model.BaseEntity;
import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class User extends BaseEntity {

    private static final int USERNAME_SUFFIX_LENGTH = 8;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "username", length = 100, unique = true)
    private String username;

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

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

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
        user.username = generateUsernamePrefix(email) + generateUsernameSuffix();
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
        user.username = generateUsernamePrefix(email) + generateUsernameSuffix();
        return user;
    }

    public void linkOAuth(String oauthId, String fullName, String avatarUrl) {
        this.oauthProvider = OauthProvider.GOOGLE;
        this.oauthId = oauthId;
        if ((this.fullName == null || this.fullName.isBlank()) && fullName != null && !fullName.isBlank()) {
            this.fullName = fullName;
        }
        if ((this.avatarUrl == null || this.avatarUrl.isBlank()) && avatarUrl != null && !avatarUrl.isBlank()) {
            this.avatarUrl = avatarUrl;
        }
    }

    public void activateFromOtp() {
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = Instant.now();
    }

    public void markDeletionRequested(Instant when) {
        this.status = UserStatus.PENDING_DELETION;
        this.deletionRequestedAt = when;
    }

    public void cancelDeletion() {
        this.status = UserStatus.ACTIVE;
        this.deletionRequestedAt = null;
    }

    public void anonymize(UUID userId) {
        this.username = "deleted_user_" + userId;
        this.email = "deleted_" + userId + "@pwbmini.com";
        this.passwordHash = null;
        this.fullName = null;
        this.phone = null;
        this.avatarUrl = null;
        this.oauthProvider = OauthProvider.LOCAL;
        this.oauthId = null;
        this.status = UserStatus.DELETED;
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
        return UUID.randomUUID().toString().replace("-", "").substring(0, USERNAME_SUFFIX_LENGTH);
    }
}
