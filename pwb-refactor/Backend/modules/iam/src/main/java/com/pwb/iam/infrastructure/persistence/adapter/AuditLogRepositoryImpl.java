package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.audit.AuditLogEntry;
import com.pwb.iam.domain.repository.AuditLogRepository;
import com.pwb.iam.infrastructure.persistence.entity.AuditLogJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.AuditLogMapper;
import com.pwb.iam.infrastructure.persistence.repository.AuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository repository;
    private final AuditLogMapper mapper;

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        AuditLogJpaEntity entity = mapper.toEntity(entry);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        AuditLogJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}