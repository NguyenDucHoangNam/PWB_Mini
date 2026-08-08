package com.pwb.iam.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface IamJpaRepository<T> extends JpaRepository<T, UUID> {
}
