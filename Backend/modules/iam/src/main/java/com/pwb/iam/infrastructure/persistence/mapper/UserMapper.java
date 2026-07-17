package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.core.model.EmailAddress;
import com.pwb.iam.core.model.Role;
import com.pwb.iam.core.model.RoleName;
import com.pwb.iam.core.model.User;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = { RoleMapper.class }
)
public interface UserMapper {

    default UserJpaEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        RoleJpaEntity roleEntity = mapRoleToEntity(domain.getRole());
        return UserJpaEntity.builder()
                .username(domain.getUsername())
                .email(domain.getEmail() == null ? null : domain.getEmail().value())
                .password(domain.getPassword() == null ? null : domain.getPassword().getHash())
                .fullName(domain.getFullName())
                .avatarUrl(domain.getAvatarUrl())
                .phone(domain.getPhone())
                .status(domain.getStatus())
                .role(roleEntity)
                .oauthProvider(domain.getOauthProvider())
                .oauthId(domain.getOauthId())
                .deletionRequestedAt(domain.getDeletionRequestedAt())
                .build();
    }

    default User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        EmailAddress email = entity.getEmail() == null ? null : EmailAddress.of(entity.getEmail());
        Role role = mapRole(entity.getRole());
        return User.rehydrate(
                entity.getId(),
                entity.getUsername(),
                email,
                entity.getPassword(),
                entity.getFullName(),
                entity.getAvatarUrl(),
                entity.getPhone(),
                entity.getStatus(),
                role,
                entity.getOauthProvider(),
                entity.getOauthId(),
                entity.getDeletionRequestedAt()
        );
    }

    default Role mapRole(RoleJpaEntity entity) {
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

    default RoleJpaEntity mapRoleToEntity(Role domain) {
        if (domain == null || domain.getRoleId() == null) {
            return null;
        }
        return RoleJpaEntity.builder()
                .name(domain.getRoleId().name())
                .description(domain.getDescription())
                .build();
    }
}