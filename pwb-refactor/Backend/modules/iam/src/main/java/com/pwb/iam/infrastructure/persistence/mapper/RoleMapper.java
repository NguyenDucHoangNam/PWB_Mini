package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

    public Role toDomain(RoleJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        RoleName name;
        try {
            name = entity.getName() == null ? null : RoleName.valueOf(entity.getName());
        } catch (IllegalArgumentException ex) {
            name = null;
        }
        return Role.of(name, entity.getDescription());
    }

    public RoleJpaEntity toEntity(Role domain) {
        if (domain == null) {
            return null;
        }
        return RoleJpaEntity.builder()
                .name(domain.getName() == null ? null : domain.getName().name())
                .description(domain.getDescription())
                .build();
    }
}
