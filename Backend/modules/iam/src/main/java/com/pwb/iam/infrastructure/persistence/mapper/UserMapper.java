package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserMapper {

    private final RoleRepository roleRepository;
    private final RoleJpaRepository roleJpaRepository;

    public UserMapper(RoleRepository roleRepository, RoleJpaRepository roleJpaRepository) {
        this.roleRepository = roleRepository;
        this.roleJpaRepository = roleJpaRepository;
    }

    public UserJpaEntity toEntity(User domain, UserJpaEntity existing) {
        if (domain == null) {
            return null;
        }
        RoleJpaEntity managedRole = resolveManagedRole(domain.getRole());

        if (existing != null) {
            existing.setEmail(domain.getEmail() == null ? null : domain.getEmail().value());
            existing.setPassword(domain.getPassword() == null ? null : domain.getPassword().hash());
            existing.setFullName(domain.getFullName());
            existing.setAvatarUrl(domain.getAvatarUrl());
            existing.setPhone(domain.getPhone());
            existing.setStatus(domain.getStatus());
            existing.setRole(managedRole);
            existing.setOauthProvider(domain.getOauthProvider());
            existing.setOauthId(domain.getOauthId());
            existing.setBanReason(domain.getBanReason());
            existing.setBannedAt(domain.getBannedAt());
            existing.setBannedBy(domain.getBannedBy());
            return existing;
        }
        return UserJpaEntity.builder()
                .email(domain.getEmail() == null ? null : domain.getEmail().value())
                .password(domain.getPassword() == null ? null : domain.getPassword().hash())
                .fullName(domain.getFullName())
                .avatarUrl(domain.getAvatarUrl())
                .phone(domain.getPhone())
                .status(domain.getStatus())
                .role(managedRole)
                .oauthProvider(domain.getOauthProvider() == null ? OAuthProvider.LOCAL : domain.getOauthProvider())
                .oauthId(domain.getOauthId())
                .banReason(domain.getBanReason())
                .bannedAt(domain.getBannedAt())
                .bannedBy(domain.getBannedBy())
                .build();
    }

    private RoleJpaEntity resolveManagedRole(com.pwb.iam.domain.model.RoleName roleName) {
        if (roleName == null) {
            return null;
        }
        Optional<RoleJpaEntity> managed = roleJpaRepository.findByNameAndDeletedFalse(roleName.name());
        return managed.orElse(null);
    }

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        String roleName = entity.getRole() == null ? null : entity.getRole().getName();
        return User.rehydrate(
                entity.getId(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getFullName(),
                entity.getAvatarUrl(),
                entity.getPhone(),
                entity.getStatus() == null ? UserStatus.PENDING_VERIFICATION : entity.getStatus(),
                roleName,
                entity.getOauthProvider(),
                entity.getOauthId(),
                entity.getBanReason(),
                entity.getBannedAt(),
                entity.getBannedBy()
        );
    }
}
