package com.pwb.audio.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

/**
 * {@link JpaSpecificationExecutor} is here for search: the filter combination a caller sends is open-ended,
 * and composing it from a specification keeps one query covering every combination rather than a derived
 * method per shape.
 */
@NoRepositoryBean
public interface AudioJpaRepository<T> extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {
}
