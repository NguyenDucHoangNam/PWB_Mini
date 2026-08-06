package com.pwb.audio.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

/**
 * {@link JpaSpecificationExecutor} is here for the search fallback: when Elasticsearch is unavailable the
 * same filter combination has to be expressed against the database, and building it from a specification
 * keeps the two paths returning the same rows rather than a subset.
 */
@NoRepositoryBean
public interface AudioJpaRepository<T> extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {
}
