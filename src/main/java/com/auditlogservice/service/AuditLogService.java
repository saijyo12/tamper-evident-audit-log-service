package com.auditlogservice.service;

import com.auditlogservice.dto.AuditLogResponse;
import com.auditlogservice.dto.AuditLogPageResponse;
import com.auditlogservice.dto.CreateAuditLogRequest;
import com.auditlogservice.dto.IntegrityVerificationResponse;
import com.auditlogservice.dto.RetentionResponse;
import com.auditlogservice.dto.AuditLogExportBundle;
import java.time.Instant;
import java.util.UUID;

public interface AuditLogService {
    AuditLogResponse append(CreateAuditLogRequest request);

    AuditLogResponse getById(UUID id);

    AuditLogPageResponse query(String actorId, String resourceType, String resourceId, String eventType,
            Instant from, Instant to, int page, int size);

    IntegrityVerificationResponse verifyIntegrity();

    AuditLogResponse redact(UUID id, java.util.List<String> fields);

    RetentionResponse archiveOlderThan(long olderThanDays);

    AuditLogExportBundle export(String actorId, String resourceId);
}
