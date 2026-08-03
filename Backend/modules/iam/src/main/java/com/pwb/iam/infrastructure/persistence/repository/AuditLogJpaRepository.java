package com.pwb.iam.infrastructure.persistence.repository;

import com.pwb.iam.infrastructure.persistence.entity.AuditLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, UUID> {
}