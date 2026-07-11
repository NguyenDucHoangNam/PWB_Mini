package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.model.DemoRevokeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemoRevokeAuditRepository extends JpaRepository<DemoRevokeAudit, String> {
}