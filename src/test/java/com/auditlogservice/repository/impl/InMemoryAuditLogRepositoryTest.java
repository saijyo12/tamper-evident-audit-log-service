package com.auditlogservice.repository.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.auditlogservice.model.AuditLogEntry;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InMemoryAuditLogRepositoryTest {
    private final InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();

    @Test
    void appendsFindsAndPreservesInsertionOrder() {
        AuditLogEntry first = entry(UUID.randomUUID(), "hash-1");
        AuditLogEntry second = entry(UUID.randomUUID(), "hash-2");
        repository.append(first);
        repository.append(second);

        assertEquals(first, repository.findById(first.id()).orElseThrow());
        assertEquals(java.util.List.of(first, second), repository.findAll());
        assertEquals(second, repository.findLatest().orElseThrow());
    }

    @Test
    void replacesOnlyExistingEntriesAndEmptyRepositoryHasNoLatest() {
        assertTrue(repository.findLatest().isEmpty());
        AuditLogEntry original = entry(UUID.randomUUID(), "hash-1");
        repository.append(original);
        AuditLogEntry replacement = new AuditLogEntry(original.id(), original.eventType(), original.actorId(),
                original.resourceType(), original.resourceId(), original.payload(), original.timestamp(),
                original.previousHash(), original.hash(), original.payloadCommitment(), original.fieldCommitments(),
                original.fieldSalts(), original.redactedFields(), Instant.now());

        assertEquals(replacement, repository.replace(replacement));
        assertNotNull(repository.findById(original.id()).orElseThrow().archivedAt());
        assertThrows(IllegalArgumentException.class, () -> repository.replace(entry(UUID.randomUUID(), "unknown")));
    }

    private AuditLogEntry entry(UUID id, String hash) {
        return new AuditLogEntry(id, "EVENT", "actor", "type", "resource",
                JsonMapper.builder().build().createObjectNode().put("key", "value"), Instant.now(),
                "GENESIS", hash, "commitment", Map.of("/key", "field-hash"),
                Map.of("/key", "salt"), Set.of(), null);
    }
}
