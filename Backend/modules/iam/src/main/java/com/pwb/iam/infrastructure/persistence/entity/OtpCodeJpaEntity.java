package com.pwb.iam.infrastructure.persistence.entity;

import com.pwb.iam.domain.model.OtpCode.OtpStatus;
import com.pwb.iam.domain.model.OtpPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_codes")
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpCodeJpaEntity extends IamJpaBaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private OtpPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OtpStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;
}
