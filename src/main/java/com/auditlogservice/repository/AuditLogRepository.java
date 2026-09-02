package com.auditlogservice.repository;

import com.auditlogservice.model.AuditLogEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository {
    AuditLogEntry append(AuditLogEntry entry);

    AuditLogEntry replace(AuditLogEntry entry);

    Optional<AuditLogEntry> findById(UUID id);

    List<AuditLogEntry> findAll();

    Optional<AuditLogEntry> findLatest();
}
