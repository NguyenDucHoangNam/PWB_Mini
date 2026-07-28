package com.pwb.iam.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class PasswordResetToken extends BaseEntity {

    private UUID tokenId;
    private final UUID userId;
    private String tokenHash;
    private Instant expiresAt;
    private boolean used;
    private Instant usedAt;

    private PasswordResetToken(
            UUID userId,
            String tokenHash,
            Instant expiresAt
    ) {
        this.tokenId = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.used = false;
        this.usedAt = null;
    }

    public static PasswordResetToken create(UUID userId, String tokenHash, Instant expiresAt) {
        return new PasswordResetToken(userId, tokenHash, expiresAt);
    }

    public static PasswordResetToken rehydrate(
            UUID tokenId,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            boolean used,
            Instant usedAt
    ) {
        PasswordResetToken token = new PasswordResetToken(userId, tokenHash, expiresAt);
        token.tokenId = tokenId;
        token.used = used;
        token.usedAt = usedAt;
        return token;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.used = true;
        this.usedAt = now;
        touch();
    }
}
