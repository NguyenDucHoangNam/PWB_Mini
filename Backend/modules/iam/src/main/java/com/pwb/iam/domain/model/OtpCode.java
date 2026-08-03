package com.pwb.iam.domain.model;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.OtpVerificationException;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class OtpCode extends DomainBaseEntity {

    public static final int MAX_ATTEMPTS = 5;

    private final UUID id;
    private final UUID userId;
    private final OtpPurpose purpose;
    private final String codeHash;
    private OtpStatus status;
    private int attempts;
    private final Instant expiresAt;
    private Instant verifiedAt;

    private OtpCode(UUID id, UUID userId, OtpPurpose purpose, String codeHash, Instant expiresAt) {
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("codeHash must not be blank");
        }
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.status = OtpStatus.PENDING;
        this.attempts = 0;
        this.expiresAt = expiresAt;
    }

    public static OtpCode create(UUID userId, OtpPurpose purpose, String codeHash, Instant expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        return new OtpCode(UUID.randomUUID(), userId, purpose, codeHash, expiresAt);
    }

    public static OtpCode rehydrate(
            UUID id,
            UUID userId,
            OtpPurpose purpose,
            String codeHash,
            OtpStatus status,
            int attempts,
            Instant expiresAt,
            Instant verifiedAt
    ) {
        OtpCode code = new OtpCode(id, userId, purpose, codeHash, expiresAt);
        code.status = status;
        code.attempts = attempts;
        code.verifiedAt = verifiedAt;
        return code;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OtpStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public void markVerified(Instant now) {
        this.status = OtpStatus.VERIFIED;
        this.verifiedAt = now;
        touch();
    }

    public void registerFailedAttempt() {
        this.attempts += 1;
        if (this.attempts >= MAX_ATTEMPTS) {
            this.attempts = MAX_ATTEMPTS;
            this.status = OtpStatus.LOCKED;
        }
        touch();
    }

    public boolean hasReachedMaxAttempts() {
        return this.attempts >= MAX_ATTEMPTS;
    }

    public boolean isLocked() {
        return status == OtpStatus.LOCKED || attempts >= MAX_ATTEMPTS;
    }

    public boolean isPending() {
        return status == OtpStatus.PENDING;
    }

    public void verify(String rawCode, OtpGenerator generator) {
        if (isLocked()) {
            throw new OtpVerificationException(IamErrorCode.AUTH_OTP_INVALID,
                    Map.of("maxAttemptsReached", true));
        }
        if (isExpired(Instant.now())) {
            throw new OtpVerificationException(IamErrorCode.AUTH_OTP_EXPIRED);
        }
        if (!generator.matches(rawCode, codeHash)) {
            registerFailedAttempt();
            if (isLocked()) {
                throw new OtpVerificationException(IamErrorCode.AUTH_OTP_INVALID,
                        Map.of("maxAttemptsReached", true));
            }
            throw new OtpVerificationException(IamErrorCode.AUTH_OTP_INVALID);
        }
        markVerified(Instant.now());
    }

    public enum OtpStatus {
        PENDING,
        VERIFIED,
        EXPIRED,
        LOCKED
    }
}
