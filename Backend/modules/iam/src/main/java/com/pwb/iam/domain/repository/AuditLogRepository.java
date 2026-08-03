package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.audit.AuditLogEntry;

public interface AuditLogRepository {

    AuditLogEntry save(AuditLogEntry entry);
}