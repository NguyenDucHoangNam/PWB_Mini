package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordResetTokenMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
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

@DisplayName("PasswordResetTokenRepositoryImpl — H2 integration")
class PasswordResetTokenRepositoryImplIT extends AbstractRepositoryIT {

    @Autowired private PasswordResetTokenJpaRepository jpaRepository;
    @Autowired private PasswordResetTokenMapper mapper;

    private PasswordResetTokenRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new PasswordResetTokenRepositoryImpl(jpaRepository, mapper);
        jpaRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    private PasswordResetToken newToken(UUID userId, Instant expiresAt) {
        return PasswordResetToken.create(userId, "hashed:reset-token", expiresAt);
    }

    @Test
    @DisplayName("should_save_and_find_by_id")
    void should_save_and_find_by_id() {
        PasswordResetToken token = newToken(UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));

        PasswordResetToken saved = repository.save(token);

        assertThat(saved.getTokenId()).isNotNull();
        PasswordResetToken found = repository.findById(saved.getTokenId()).orElseThrow();
        assertThat(found.getTokenHash()).isEqualTo("hashed:reset-token");
        assertThat(found.isUsed()).isFalse();
    }

    @Test
    @DisplayName("should_return_empty_when_id_not_found")
    void should_return_empty_when_id_not_found() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should_find_active_by_hash_when_not_expired_and_unused")
    void should_find_active_by_hash_when_not_expired_and_unused() {
        PasswordResetToken token = newToken(UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        repository.save(token);

        assertThat(repository.findActiveByHash("hashed:reset-token", Instant.now())).isPresent();
    }

    @Test
    @DisplayName("should_return_empty_when_hash_blank_or_null")
    void should_return_empty_when_hash_blank_or_null() {
        assertThat(repository.findActiveByHash(null, Instant.now())).isEmpty();
        assertThat(repository.findActiveByHash("", Instant.now())).isEmpty();
        assertThat(repository.findActiveByHash("   ", Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("should_return_empty_when_token_expired")
    void should_return_empty_when_token_expired() {
        PasswordResetToken token = newToken(UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.MINUTES));
        repository.save(token);

        assertThat(repository.findActiveByHash("hashed:reset-token", Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("should_invalidate_all_for_user_and_skip_used_tokens")
    void should_invalidate_all_for_user_and_skip_used_tokens() {
        UUID userId = UUID.randomUUID();
        PasswordResetToken t1 = repository.save(newToken(userId,
                Instant.now().plus(30, ChronoUnit.MINUTES)));
        repository.save(newToken(userId, Instant.now().plus(30, ChronoUnit.MINUTES)));

        int invalidated = repository.invalidateAllForUser(userId, Instant.now());

        assertThat(invalidated).isEqualTo(2);
        assertThat(repository.findActiveByHash(t1.getTokenHash(), Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("should_skip_soft_deleted_token_in_findById")
    void should_skip_soft_deleted_token_in_findById() {
        PasswordResetToken saved = repository.save(newToken(UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES)));
        UUID id = saved.getTokenId();
        jpaRepository.findById(id).ifPresent(e -> {
            e.setDeleted(true);
            jpaRepository.saveAndFlush(e);
        });

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("should_delete_by_id")
    void should_delete_by_id() {
        PasswordResetToken saved = repository.save(newToken(UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES)));
        UUID id = saved.getTokenId();

        repository.deleteById(id);

        assertThat(jpaRepository.existsById(id)).isFalse();
    }
}