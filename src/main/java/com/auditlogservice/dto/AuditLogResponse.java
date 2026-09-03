package com.auditlogservice.dto;

import com.auditlogservice.model.AuditLogEntry;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        JsonNode payload,
        Instant timestamp,
        String previousHash,
        String hash,
        String payloadCommitment,
        Map<String, String> fieldCommitments,
        Map<String, String> fieldSalts,
        Set<String> redactedFields,
        Instant archivedAt) {

    public static AuditLogResponse from(AuditLogEntry entry) {
        return new AuditLogResponse(entry.id(), entry.eventType(), entry.actorId(),
                entry.resourceType(), entry.resourceId(), entry.payload(), entry.timestamp(),
                entry.previousHash(), entry.hash(), entry.payloadCommitment(), entry.fieldCommitments(),
                entry.fieldSalts(), entry.redactedFields(), entry.archivedAt());
    }
}
