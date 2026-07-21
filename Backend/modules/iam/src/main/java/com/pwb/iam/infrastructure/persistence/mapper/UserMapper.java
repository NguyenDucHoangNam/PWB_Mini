package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.core.model.EmailAddress;
import com.pwb.iam.core.model.Role;
import com.pwb.iam.core.model.RoleName;
import com.pwb.iam.core.model.User;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.stereotype.Component;

@Component
public abstract class UserMapper {

    public UserJpaEntity toEntity(User domain) {
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
                .provisionalUsername(domain.isProvisionalUsername())
                .build();
    }

    public UserJpaEntity toEntity(User domain, UserJpaEntity existing) {
        if (domain == null) {
            return null;
        }
        existing.setUsername(domain.getUsername());
        existing.setEmail(domain.getEmail() == null ? null : domain.getEmail().value());
        existing.setPassword(domain.getPassword() == null ? null : domain.getPassword().getHash());
        existing.setFullName(domain.getFullName());
        existing.setAvatarUrl(domain.getAvatarUrl());
        existing.setPhone(domain.getPhone());
        existing.setStatus(domain.getStatus());
        existing.setOauthProvider(domain.getOauthProvider());
        existing.setOauthId(domain.getOauthId());
        existing.setDeletionRequestedAt(domain.getDeletionRequestedAt());
        existing.setProvisionalUsername(domain.isProvisionalUsername());
        return existing;
    }

    public User toDomain(UserJpaEntity entity) {
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
                entity.getDeletionRequestedAt(),
                entity.isProvisionalUsername()
        );
    }

    private Role mapRole(RoleJpaEntity entity) {
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

    private RoleJpaEntity mapRoleToEntity(Role domain) {
        if (domain == null || domain.getRoleId() == null) {
            return null;
        }
        return RoleJpaEntity.builder()
                .name(domain.getRoleId().name())
                .description(domain.getDescription())
                .build();
    }

    @Component
    public static class UserMapperImpl extends UserMapper {
    }
}