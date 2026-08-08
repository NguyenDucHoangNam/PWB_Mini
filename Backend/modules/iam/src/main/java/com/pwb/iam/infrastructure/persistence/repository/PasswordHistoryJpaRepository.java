package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.infrastructure.persistence.entity.PasswordHistoryJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface PasswordHistoryJpaRepository extends IamJpaRepository<PasswordHistoryJpaEntity> {

    List<PasswordHistoryJpaEntity> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndDeletedFalse(UUID userId);

    @Transactional
    @Modifying
    @Query(value = """
            DELETE FROM iam_password_history
            WHERE id IN (
                SELECT id FROM iam_password_history
                WHERE user_id = :userId AND deleted = false
                ORDER BY created_at ASC
                LIMIT :count
            )
            """, nativeQuery = true)
    void deleteOldestByUserId(@Param("userId") UUID userId, @Param("count") int count);
}
