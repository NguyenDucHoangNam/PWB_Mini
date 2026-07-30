package com.pwb.iam.infrastructure.persistence.entity;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "iam_users",
        indexes = {
                @Index(name = "ix_iam_users_email", columnList = "email"),
                @Index(name = "ix_iam_users_oauth", columnList = "oauth_provider, oauth_id"),
                @Index(name = "ix_iam_users_role_id", columnList = "role_id"),
                @Index(name = "ix_iam_users_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_iam_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_iam_users_oauth", columnNames = {"oauth_provider", "oauth_id"})
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJpaEntity extends IamJpaBaseEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "phone", length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private RoleJpaEntity role;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 16)
    private OAuthProvider oauthProvider;

    @Column(name = "oauth_id", length = 255)
    private String oauthId;

    public UUID getRoleId() {
        return role == null ? null : role.getId();
    }
}
