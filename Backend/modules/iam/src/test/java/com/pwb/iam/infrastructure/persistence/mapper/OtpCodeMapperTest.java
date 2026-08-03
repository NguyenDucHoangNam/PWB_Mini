package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OtpCodeMapperTest {

    private final OtpCodeMapper mapper = new OtpCodeMapper();

    @Test
    @DisplayName("toEntity should map all fields from domain")
    void should_map_to_entity() {
        Instant expiresAt = Instant.parse("2026-12-31T00:00:00Z");
        OtpCode otp = OtpCode.create(UUID.randomUUID(), OtpPurpose.REGISTER, "hash", expiresAt);

        OtpCodeJpaEntity entity = mapper.toEntity(otp);

        assertThat(entity.getUserId()).isEqualTo(otp.getUserId());
        assertThat(entity.getPurpose()).isEqualTo(OtpPurpose.REGISTER);
        assertThat(entity.getCodeHash()).isEqualTo("hash");
        assertThat(entity.getStatus()).isEqualTo(OtpCode.OtpStatus.PENDING);
        assertThat(entity.getAttempts()).isZero();
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("toEntity with existing entity should update mutable fields")
    void should_update_existing_entity() {
        OtpCode otp = OtpCode.create(UUID.randomUUID(), OtpPurpose.REGISTER, "hash", Instant.now().plusSeconds(300));
        otp.registerFailedAttempt();
        otp.markVerified(Instant.now());

        OtpCodeJpaEntity existing = OtpCodeJpaEntity.builder()
                .userId(UUID.randomUUID())
                .purpose(OtpPurpose.PASSWORD_RESET)
                .codeHash("old-hash")
                .status(OtpCode.OtpStatus.PENDING)
                .attempts(0)
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        OtpCodeJpaEntity updated = mapper.toEntity(otp, existing);

        assertThat(updated).isSameAs(existing);
        assertThat(updated.getStatus()).isEqualTo(OtpCode.OtpStatus.VERIFIED);
        assertThat(updated.getAttempts()).isEqualTo(1);
        assertThat(updated.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("toDomain should map back")
    void should_map_to_domain() {
        Instant expiresAt = Instant.parse("2026-12-31T00:00:00Z");
        OtpCodeJpaEntity entity = OtpCodeJpaEntity.builder()
                .userId(UUID.randomUUID())
                .purpose(OtpPurpose.PASSWORD_RESET)
                .codeHash("hash")
                .status(OtpCode.OtpStatus.LOCKED)
                .attempts(5)
                .expiresAt(expiresAt)
                .verifiedAt(Instant.now())
                .build();

        OtpCode otp = mapper.toDomain(entity);

        assertThat(otp.getStatus()).isEqualTo(OtpCode.OtpStatus.LOCKED);
        assertThat(otp.getAttempts()).isEqualTo(5);
        assertThat(otp.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(otp.getPurpose()).isEqualTo(OtpPurpose.PASSWORD_RESET);
    }

    @Test
    @DisplayName("getId should return entity id or null")
    void should_return_id_or_null() {
        OtpCodeJpaEntity entity = OtpCodeJpaEntity.builder()
                .userId(UUID.randomUUID())
                .purpose(OtpPurpose.REGISTER)
                .codeHash("hash")
                .expiresAt(Instant.now())
                .build();
        entity.setId(UUID.randomUUID());

        assertThat(mapper.getId(entity)).isEqualTo(entity.getId());
        assertThat(mapper.getId(null)).isNull();
    }

    @Test
    @DisplayName("toEntity with null domain returns null")
    void should_handle_null_domain() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    @DisplayName("toDomain with null entity returns null")
    void should_handle_null_entity() {
        assertThat(mapper.toDomain(null)).isNull();
    }
}