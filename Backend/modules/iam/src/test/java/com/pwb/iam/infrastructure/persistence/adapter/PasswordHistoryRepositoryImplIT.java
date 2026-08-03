package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordHistoryMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordHistoryJpaRepository;
import com.pwb.iam.testsupport.AbstractRepositoryIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordHistoryRepositoryImpl — H2 integration")
class PasswordHistoryRepositoryImplIT extends AbstractRepositoryIT {

    @Autowired private PasswordHistoryJpaRepository jpaRepository;
    @Autowired private PasswordHistoryMapper mapper;

    private PasswordHistoryRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new PasswordHistoryRepositoryImpl(jpaRepository, mapper);
        jpaRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    private PasswordHistory entry(UUID userId, String hash) {
        return PasswordHistory.create(userId, hash);
    }

    @Test
    @DisplayName("should_save_history_entry")
    void should_save_history_entry() {
        UUID userId = UUID.randomUUID();

        repository.save(entry(userId, "hashed:v1"));

        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_find_by_user_id_order_by_created_at_desc_with_limit")
    void should_find_by_user_id_order_by_created_at_desc_with_limit() throws Exception {
        UUID userId = UUID.randomUUID();
        repository.save(entry(userId, "hashed:v1"));
        Thread.sleep(10);
        repository.save(entry(userId, "hashed:v2"));
        Thread.sleep(10);
        repository.save(entry(userId, "hashed:v3"));

        var result = repository.findByUserIdOrderByCreatedAtDesc(userId, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPasswordHash()).isEqualTo("hashed:v3");
        assertThat(result.get(1).getPasswordHash()).isEqualTo("hashed:v2");
    }

    @Test
    @DisplayName("should_return_empty_list_when_no_history")
    void should_return_empty_list_when_no_history() {
        assertThat(repository.findByUserIdOrderByCreatedAtDesc(UUID.randomUUID(), 5)).isEmpty();
    }

    @Test
    @DisplayName("should_count_by_user_id")
    void should_count_by_user_id() {
        UUID userId = UUID.randomUUID();
        repository.save(entry(userId, "hashed:v1"));
        repository.save(entry(userId, "hashed:v2"));

        assertThat(repository.countByUserId(userId)).isEqualTo(2L);
        assertThat(repository.countByUserId(UUID.randomUUID())).isZero();
    }

    @Test
    @DisplayName("should_skip_soft_deleted_history_in_findByUserIdOrderByCreatedAtDesc")
    void should_skip_soft_deleted_history_in_findByUserIdOrderByCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        repository.save(entry(userId, "hashed:v1"));
        var all = jpaRepository.findAll();
        all.forEach(e -> {
            e.setDeleted(true);
            jpaRepository.saveAndFlush(e);
        });

        assertThat(repository.findByUserIdOrderByCreatedAtDesc(userId, 5)).isEmpty();
        assertThat(repository.countByUserId(userId)).isZero();
    }

    @Test
    @DisplayName("should_delete_oldest_by_user_id_via_native_query")
    void should_delete_oldest_by_user_id_via_native_query() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            repository.save(entry(userId, "hashed:v" + i));
        }

        repository.deleteOldestByUserId(userId, 2);

        assertThat(repository.countByUserId(userId)).isEqualTo(4L);
    }
}