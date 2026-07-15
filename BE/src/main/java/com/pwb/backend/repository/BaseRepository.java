package com.pwb.backend.repository;

import com.pwb.backend.entity.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity> extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {

    Optional<T> findByIdAndDeletedFalse(UUID id);

    Optional<T> findByIdAndDeletedTrue(UUID id);
}
