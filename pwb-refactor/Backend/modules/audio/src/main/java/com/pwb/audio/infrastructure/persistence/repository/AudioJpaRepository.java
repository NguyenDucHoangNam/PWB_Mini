package com.pwb.audio.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface AudioJpaRepository<T> extends JpaRepository<T, UUID> {
}
