package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.RoleMapper;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleJpaRepository roleJpaRepository;
    private final RoleMapper roleMapper;

    @Override
    public Role save(Role role) {
        RoleJpaEntity existing = roleJpaRepository.findByName(role.getName())
                .orElse(null);
        RoleJpaEntity entity = roleMapper.toEntity(role);
        if (existing != null) {
            existing.setName(entity.getName());
            existing.setDescription(entity.getDescription());
            entity = existing;
        }
        RoleJpaEntity saved = roleJpaRepository.save(entity);
        return roleMapper.toDomain(saved);
    }

    @Override
    public Optional<Role> findByName(RoleName name) {
        if (name == null) {
            return Optional.empty();
        }
        return roleJpaRepository.findByNameAndDeletedFalse(name.name())
                .map(roleMapper::toDomain);
    }
}
