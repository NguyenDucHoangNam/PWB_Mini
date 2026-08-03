package com.pwb.iam.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;

public final class PasswordResetToken extends DomainBaseEntity {

    private final UUID tokenId;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private boolean used;
    private Instant usedAt;

    private PasswordResetToken(UUID tokenId, UUID userId, String tokenHash, Instant expiresAt) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        this.tokenId = tokenId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.used = false;
        this.usedAt = null;
    }

    public static PasswordResetToken create(UUID userId, String tokenHash, Instant expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return new PasswordResetToken(UUID.randomUUID(), userId, tokenHash, expiresAt);
    }

    public static PasswordResetToken rehydrate(
            UUID tokenId,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            boolean used,
            Instant usedAt
    ) {
        PasswordResetToken token = new PasswordResetToken(tokenId, userId, tokenHash, expiresAt);
        token.used = used;
        token.usedAt = usedAt;
        return token;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public boolean isUsable() {
        return !used && !isExpired(java.time.Instant.now());
    }

    public void markUsed(Instant now) {
        this.used = true;
        this.usedAt = now;
        touch();
    }
}
