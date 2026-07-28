package com.pwb.iam.application.audit;

import com.pwb.iam.domain.audit.AuditLogEntry;

public record AuditPersistRequested(AuditLogEntry entry) {
}