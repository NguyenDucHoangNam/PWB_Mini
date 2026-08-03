package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.PasswordHistory;
import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepository {

    void save(PasswordHistory history);

    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, int limit);

    void deleteOldestByUserId(UUID userId, int count);

    long countByUserId(UUID userId);
}
