package com.pwb.iam.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "iam_password_reset_tokens",
    indexes = {
        @Index(name = "ix_iam_password_reset_tokens_user_id", columnList = "user_id"),
        @Index(name = "ix_iam_password_reset_tokens_expires_at", columnList = "expires_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_iam_password_reset_tokens_hash", columnNames = "token_hash")
    }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetTokenJpaEntity extends IamJpaBaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private Instant usedAt;

    public void markUsed(Instant now) {
        this.used = true;
        this.usedAt = now;
    }
}
