package com.auditlogservice.dto;

import com.auditlogservice.model.AuditLogEntry;
import java.time.Instant;
import java.util.UUID;

/** Payload-free metadata needed to verify the complete chain and export-filter completeness. */
public record ExportChainLink(
        UUID id,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Instant timestamp,
        String previousHash,
        String hash,
        String payloadCommitment) {

    public static ExportChainLink from(AuditLogEntry entry) {
        return new ExportChainLink(entry.id(), entry.eventType(), entry.actorId(), entry.resourceType(),
                entry.resourceId(), entry.timestamp(), entry.previousHash(), entry.hash(), entry.payloadCommitment());
    }
}
