package com.auditlogservice.exception;

import java.util.UUID;

public class AuditLogNotFoundException extends RuntimeException {
    public AuditLogNotFoundException(UUID id) {
        super("Audit log entry not found: " + id);
    }
}
