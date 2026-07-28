package com.pwb.iam.infrastructure.audit;

import com.pwb.iam.domain.audit.AuditLogEntry;

public record AuditPersistRequested(AuditLogEntry entry) {
}