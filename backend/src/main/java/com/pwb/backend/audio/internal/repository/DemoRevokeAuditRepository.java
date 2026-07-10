package com.pwb.backend.audio.internal.repository;

import com.pwb.backend.audio.internal.model.DemoRevokeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemoRevokeAuditRepository extends JpaRepository<DemoRevokeAudit, String> {
}