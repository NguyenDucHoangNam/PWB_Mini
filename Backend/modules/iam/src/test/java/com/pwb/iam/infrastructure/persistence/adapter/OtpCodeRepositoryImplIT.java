package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.infrastructure.persistence.mapper.OtpCodeMapper;
import com.pwb.iam.infrastructure.persistence.repository.OtpCodeJpaRepository;
import com.pwb.iam.testsupport.AbstractRepositoryIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OtpCodeRepositoryImpl — H2 integration")
class OtpCodeRepositoryImplIT extends AbstractRepositoryIT {

    @Autowired private OtpCodeJpaRepository jpaRepository;
    @Autowired private OtpCodeMapper otpCodeMapper;

    private OtpCodeRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new OtpCodeRepositoryImpl(jpaRepository, otpCodeMapper);
        jpaRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    private OtpCode pendingOtp(UUID userId, OtpPurpose purpose) {
        return OtpCode.create(userId, purpose, "hashed:123456",
                Instant.now().plus(5, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("should_save_new_otp_and_find_by_id")
    void should_save_new_otp_and_find_by_id() {
        OtpCode otp = pendingOtp(UUID.randomUUID(), OtpPurpose.REGISTER);

        OtpCode saved = repository.save(otp);

        assertThat(saved.getId()).isNotNull();
        OtpCode found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCodeHash()).isEqualTo("hashed:123456");
        assertThat(found.getStatus()).isEqualTo(OtpCode.OtpStatus.PENDING);
    }

    @Test
    @DisplayName("should_return_empty_when_id_not_found")
    void should_return_empty_when_id_not_found() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should_find_active_by_user_and_purpose")
    void should_find_active_by_user_and_purpose() {
        UUID userId = UUID.randomUUID();
        repository.save(pendingOtp(userId, OtpPurpose.REGISTER));

        assertThat(repository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER)).isPresent();
        assertThat(repository.findActiveByUserAndPurpose(userId, OtpPurpose.PASSWORD_RESET)).isEmpty();
    }

    @Test
    @DisplayName("should_return_latest_active_otp_when_multiple_pending")
    void should_return_latest_active_otp_when_multiple_pending() throws Exception {
        UUID userId = UUID.randomUUID();
        OtpCode first = repository.save(pendingOtp(userId, OtpPurpose.REGISTER));
        Thread.sleep(10);
        OtpCode second = repository.save(pendingOtp(userId, OtpPurpose.REGISTER));

        OtpCode found = repository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER).orElseThrow();
        assertThat(found.getId()).isEqualTo(second.getId());
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    @DisplayName("should_delete_all_by_user_and_purpose")
    void should_delete_all_by_user_and_purpose() {
        UUID userId = UUID.randomUUID();
        repository.save(pendingOtp(userId, OtpPurpose.REGISTER));
        repository.save(pendingOtp(userId, OtpPurpose.REGISTER));
        repository.save(pendingOtp(userId, OtpPurpose.PASSWORD_RESET));

        repository.invalidateAllByUserAndPurpose(userId, OtpPurpose.REGISTER);

        assertThat(repository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER)).isEmpty();
        assertThat(repository.findActiveByUserAndPurpose(userId, OtpPurpose.PASSWORD_RESET)).isPresent();
    }

    @Test
    @DisplayName("should_count_issued_today_since_given_instant")
    void should_count_issued_today_since_given_instant() {
        UUID userId = UUID.randomUUID();
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        repository.save(pendingOtp(userId, OtpPurpose.REGISTER));
        repository.save(pendingOtp(userId, OtpPurpose.REGISTER));

        int count = repository.countIssuedSince(userId, OtpPurpose.REGISTER, oneHourAgo);
        assertThat(count).isEqualTo(2);
        assertThat(repository.countIssuedSince(userId, OtpPurpose.PASSWORD_RESET, oneHourAgo)).isZero();
    }

    @Test
    @DisplayName("should_lock_otp_when_markLockedIfNotAlready_called")
    void should_lock_otp_when_markLockedIfNotAlready_called() {
        OtpCode saved = repository.save(pendingOtp(UUID.randomUUID(), OtpPurpose.REGISTER));

        boolean locked = repository.markLockedIfNotAlready(saved.getId(), OtpCode.MAX_ATTEMPTS);

        assertThat(locked).isTrue();
        OtpCode reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OtpCode.OtpStatus.LOCKED);
        assertThat(reloaded.getAttempts()).isEqualTo(OtpCode.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("should_increment_attempts_only_when_status_pending")
    void should_increment_attempts_only_when_status_pending() {
        OtpCode saved = repository.save(pendingOtp(UUID.randomUUID(), OtpPurpose.REGISTER));

        boolean incremented = repository.incrementAttempts(saved.getId());

        assertThat(incremented).isTrue();
        OtpCode reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_update_existing_otp_when_save_called_twice")
    void should_update_existing_otp_when_save_called_twice() {
        OtpCode first = repository.save(pendingOtp(UUID.randomUUID(), OtpPurpose.REGISTER));
        first.registerFailedAttempt(OtpCode.MAX_ATTEMPTS);
        first.registerFailedAttempt(OtpCode.MAX_ATTEMPTS);

        OtpCode second = repository.save(first);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getAttempts()).isEqualTo(2);
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }
}