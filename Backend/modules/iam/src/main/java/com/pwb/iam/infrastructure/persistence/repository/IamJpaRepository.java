package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.infrastructure.persistence.entity.IamJpaBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface IamJpaRepository<T extends IamJpaBaseEntity>
        extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {

    Optional<T> findByIdAndDeletedFalse(UUID id);
}