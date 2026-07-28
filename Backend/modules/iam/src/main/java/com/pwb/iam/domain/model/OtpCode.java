package com.pwb.iam.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class OtpCode extends BaseEntity {

    private UUID id;
    private final UUID userId;
    private final OtpPurpose purpose;
    private OtpStatus status;
    private int attempts;
    private final Instant expiresAt;
    private Instant verifiedAt;
    private Instant lockedAt;

    private OtpCode(UUID userId, OtpPurpose purpose, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.purpose = purpose;
        this.status = OtpStatus.PENDING;
        this.attempts = 0;
        this.expiresAt = expiresAt;
    }

    public static OtpCode create(UUID userId, OtpPurpose purpose, Instant expiresAt) {
        return new OtpCode(userId, purpose, expiresAt);
    }

    public static OtpCode rehydrate(
            UUID id,
            UUID userId,
            OtpPurpose purpose,
            OtpStatus status,
            int attempts,
            Instant expiresAt,
            Instant verifiedAt,
            Instant lockedAt
    ) {
        OtpCode code = new OtpCode(userId, purpose, expiresAt);
        code.id = id;
        code.status = status;
        code.attempts = attempts;
        code.verifiedAt = verifiedAt;
        code.lockedAt = lockedAt;
        return code;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public void markVerified(Instant now) {
        this.status = OtpStatus.VERIFIED;
        this.verifiedAt = now;
        touch();
    }

    public void markLocked(int currentAttempts, Instant now) {
        this.status = OtpStatus.LOCKED;
        this.lockedAt = now;
        this.attempts = currentAttempts;
        touch();
    }

    public enum OtpStatus {
        PENDING,
        VERIFIED,
        EXPIRED,
        LOCKED
    }
}
