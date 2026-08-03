package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;

import java.util.Optional;

public interface RoleJpaRepository extends IamJpaRepository<RoleJpaEntity> {

    Optional<RoleJpaEntity> findByNameAndDeletedFalse(String name);

    Optional<RoleJpaEntity> findByName(RoleName name);
}
