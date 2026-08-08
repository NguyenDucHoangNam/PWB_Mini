package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.domain.repository.PasswordHistoryRepository;
import com.pwb.iam.infrastructure.persistence.entity.PasswordHistoryJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordHistoryMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PasswordHistoryRepositoryImpl implements PasswordHistoryRepository {

    private final PasswordHistoryJpaRepository repository;
    private final PasswordHistoryMapper mapper;

    @Override
    @Transactional
    public void save(PasswordHistory history) {
        PasswordHistoryJpaEntity entity = mapper.toEntity(history);
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, int limit) {
        return repository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteOldestByUserId(UUID userId, int count) {
        repository.deleteOldestByUserId(userId, count);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserId(UUID userId) {
        return repository.countByUserIdAndDeletedFalse(userId);
    }
}
