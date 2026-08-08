package com.pwb.iam.domain.model;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.OtpVerificationException;
import com.pwb.iam.testsupport.StubOtpGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpCodeTest {

    private static final String CODE = "123456";

    /**
     * The attempt ceiling is no longer a constant on {@link OtpCode} — it comes from
     * {@code OtpPolicy} and is passed into every call, so the entity works for any configured
     * value. This mirrors the production default (`pwb.iam.otp.max-attempts`).
     */
    private static final int MAX_ATTEMPTS = 5;

    private UUID userId;
    private StubOtpGenerator generator;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        generator = new StubOtpGenerator().presetNextCode(CODE);
        generator.presetHash(CODE, "hashed:" + CODE);
    }

    @Test
    @DisplayName("should create PENDING otp with zero attempts")
    void should_create_pending_otp() {
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, "hash", Instant.now().plusSeconds(300));

        assertThat(otp.getId()).isNotNull();
        assertThat(otp.getUserId()).isEqualTo(userId);
        assertThat(otp.getPurpose()).isEqualTo(OtpPurpose.REGISTER);
        assertThat(otp.getStatus()).isEqualTo(OtpCode.OtpStatus.PENDING);
        assertThat(otp.getAttempts()).isZero();
        assertThat(otp.getVerifiedAt()).isNull();
        assertThat(otp.isLocked(MAX_ATTEMPTS)).isFalse();
    }

    @Test
    @DisplayName("should reject null userId / purpose / expiresAt")
    void should_reject_invalid_create() {
        Instant future = Instant.now().plusSeconds(300);

        assertThatThrownBy(() -> OtpCode.create(null, OtpPurpose.REGISTER, "hash", future))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.create(userId, null, "hash", future))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.create(userId, OtpPurpose.REGISTER, "hash", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject blank hash on construction")
    void should_reject_blank_hash() {
        Instant future = Instant.now().plusSeconds(300);

        assertThatThrownBy(() -> OtpCode.create(userId, OtpPurpose.REGISTER, "  ", future))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("verify should mark VERIFIED for correct code")
    void should_mark_verified_for_correct_code() {
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, "hashed:" + CODE, Instant.now().plusSeconds(300));

        otp.verify(CODE, generator, MAX_ATTEMPTS);

        assertThat(otp.getStatus()).isEqualTo(OtpCode.OtpStatus.VERIFIED);
        assertThat(otp.getVerifiedAt()).isNotNull();
        assertThat(otp.isLocked(MAX_ATTEMPTS)).isFalse();
    }

    @Test
    @DisplayName("verify should increment attempts on mismatch and stay PENDING until lock threshold")
    void should_increment_attempts_on_mismatch() {
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, "hashed:" + CODE, Instant.now().plusSeconds(300));

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            assertThatThrownBy(() -> otp.verify("WRONG", generator, MAX_ATTEMPTS))
                    .isInstanceOf(OtpVerificationException.class);
        }

        assertThat(otp.getAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(otp.getStatus()).isEqualTo(OtpCode.OtpStatus.PENDING);
    }

    @Test
    @DisplayName("verify should mark LOCKED after reaching MAX_ATTEMPTS")
    void should_lock_after_max_attempts() {
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, "hashed:" + CODE, Instant.now().plusSeconds(300));

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> otp.verify("WRONG", generator, MAX_ATTEMPTS))
                    .isInstanceOf(OtpVerificationException.class);
        }

        assertThat(otp.getStatus()).isEqualTo(OtpCode.OtpStatus.LOCKED);
        assertThat(otp.isLocked(MAX_ATTEMPTS)).isTrue();
        assertThat(otp.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("verify should throw AUTH_OTP_INVALID when locked")
    void should_throw_when_locked() {
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, "hashed:" + CODE, Instant.now().plusSeconds(300));
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                otp.verify("WRONG", generator, MAX_ATTEMPTS);
            } catch (OtpVerificationException ignored) {
            }
        }

        assertThatThrownBy(() -> otp.verify(CODE, generator, MAX_ATTEMPTS))
                .isInstanceOf(OtpVerificationException.class)
                .extracting(ex -> ((IamErrorCode) ((OtpVerificationException) ex).getErrorCode()).name())
                        .isEqualTo("AUTH_OTP_INVALID");
    }

    @Test
    @DisplayName("verify should throw AUTH_OTP_EXPIRED when expired")
    void should_throw_when_expired() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, "hashed:" + CODE, past);

        assertThatThrownBy(() -> otp.verify(CODE, generator, MAX_ATTEMPTS))
                .isInstanceOf(OtpVerificationException.class)
                .extracting(ex -> ((IamErrorCode) ((OtpVerificationException) ex).getErrorCode()).name())
                        .isEqualTo("AUTH_OTP_EXPIRED");

        assertThat(otp.isExpired(Instant.now())).isTrue();
    }
}