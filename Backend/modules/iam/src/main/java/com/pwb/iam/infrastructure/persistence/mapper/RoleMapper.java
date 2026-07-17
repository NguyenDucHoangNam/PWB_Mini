package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.core.model.Role;
import com.pwb.iam.core.model.RoleName;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RoleMapper {

    default Role toDomain(RoleJpaEntity entity) {
        if (entity == null || entity.getName() == null) {
            return null;
        }
        RoleName roleName;
        try {
            roleName = RoleName.valueOf(entity.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return Role.of(roleName, entity.getDescription());
    }

    default RoleJpaEntity toEntity(Role domain) {
        if (domain == null || domain.getRoleId() == null) {
            return null;
        }
        return RoleJpaEntity.builder()
                .name(domain.getRoleId().name())
                .description(domain.getDescription())
                .build();
    }
}