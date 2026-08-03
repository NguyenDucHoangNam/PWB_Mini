package com.pwb.iam.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;

public final class PasswordHistory extends DomainBaseEntity {

    public static final int MAX_HISTORY_SIZE = 5;

    private final UUID id;
    private final UUID userId;
    private final String passwordHash;
    private final Instant createdAt;

    private PasswordHistory(UUID id, UUID userId, String passwordHash, Instant createdAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        this.id = id;
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public static PasswordHistory create(UUID userId, String passwordHash) {
        return new PasswordHistory(UUID.randomUUID(), userId, passwordHash, Instant.now());
    }

    public static PasswordHistory rehydrate(UUID id, UUID userId, String passwordHash, Instant createdAt) {
        return new PasswordHistory(id, userId, passwordHash, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
