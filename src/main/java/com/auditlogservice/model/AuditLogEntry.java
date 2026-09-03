package com.auditlogservice.model;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** An immutable event stored in the append-only audit chain. */
public record AuditLogEntry(
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
}
