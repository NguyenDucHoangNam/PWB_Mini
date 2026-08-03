package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.infrastructure.persistence.entity.PasswordHistoryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PasswordHistoryMapper {

    public PasswordHistoryJpaEntity toEntity(PasswordHistory domain) {
        if (domain == null) {
            return null;
        }
        return PasswordHistoryJpaEntity.builder()
                .userId(domain.getUserId())
                .passwordHash(domain.getPasswordHash())
                .build();
    }

    public PasswordHistory toDomain(PasswordHistoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PasswordHistory.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getPasswordHash(),
                entity.getCreatedAt()
        );
    }
}
