package com.auditlogservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auditlogservice.dto.AuditLogPageResponse;
import com.auditlogservice.dto.AuditLogResponse;
import com.auditlogservice.dto.CreateAuditLogRequest;
import com.auditlogservice.dto.IntegrityVerificationResponse;
import com.auditlogservice.exception.AuditLogNotFoundException;
import com.auditlogservice.model.AuditLogEntry;
import com.auditlogservice.repository.impl.InMemoryAuditLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class HashingAuditLogServiceTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private InMemoryAuditLogRepository repository;
    private HashingAuditLogService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuditLogRepository();
        service = new HashingAuditLogService(repository, objectMapper);
    }

    @Test
    void appendsFirstEventUsingGenesisHashAndProducesAValidChain() {
        AuditLogResponse response = service.append(event("USER_LOGIN", "actor-1", "session", "s-1"));

        assertEquals("GENESIS", response.previousHash());
        assertEquals(64, response.hash().length());
        assertNotNull(response.timestamp());
        IntegrityVerificationResponse verification = service.verifyIntegrity();
        assertTrue(verification.valid());
        assertEquals(1, verification.checkedRecords());
    }

    @Test
    void chainsSubsequentEventsToThePreviousHash() {
        AuditLogResponse first = service.append(event("USER_LOGIN", "actor-1", "session", "s-1"));
        AuditLogResponse second = service.append(event("RECORD_UPDATED", "actor-1", "session", "s-1"));

        assertEquals(first.hash(), second.previousHash());
        assertNotEquals(first.hash(), second.hash());
        assertTrue(service.verifyIntegrity().valid());
    }

    @Test
    void detectsAnEventPayloadThatWasModifiedAfterItWasStored() {
        service.append(event("RECORD_UPDATED", "actor-1", "customer", "c-1"));
        ObjectNode storedPayload = (ObjectNode) repository.findAll().getFirst().payload();
        storedPayload.put("changedAfterAppend", true);

        IntegrityVerificationResponse verification = service.verifyIntegrity();

        assertFalse(verification.valid());
        assertEquals(1, verification.checkedRecords());
        assertEquals(repository.findAll().getFirst().id(), verification.firstInconsistentRecordId());
        assertEquals("HASH_MISMATCH", verification.violationType());
    }

    @Test
    void identifiesTheFirstBrokenPreviousHashLink() {
        service.append(event("USER_LOGIN", "actor-1", "session", "s-1"));
        AuditLogResponse second = service.append(event("RECORD_UPDATED", "actor-1", "session", "s-1"));
        AuditLogEntry storedSecond = repository.findAll().get(1);
        repository.append(new AuditLogEntry(storedSecond.id(), storedSecond.eventType(), storedSecond.actorId(),
                storedSecond.resourceType(), storedSecond.resourceId(), storedSecond.payload(), storedSecond.timestamp(),
                "not-the-previous-hash", storedSecond.hash()));

        IntegrityVerificationResponse verification = service.verifyIntegrity();

        assertFalse(verification.valid());
        assertEquals(second.id(), verification.firstInconsistentRecordId());
        assertEquals("PREVIOUS_HASH_MISMATCH", verification.violationType());
    }

    @Test
    void queriesUsingCombinedFiltersAndPaginatesResults() {
        service.append(event("USER_LOGIN", "actor-1", "session", "s-1"));
        service.append(event("RECORD_UPDATED", "actor-1", "session", "s-1"));
        service.append(event("RECORD_UPDATED", "actor-2", "customer", "c-1"));

        AuditLogPageResponse result = service.query("actor-1", "session", "s-1", null,
                Instant.EPOCH, Instant.now().plusSeconds(60), 1, 1);

        assertEquals(2, result.totalElements());
        assertEquals(2, result.totalPages());
        assertEquals(1, result.content().size());
        assertEquals("RECORD_UPDATED", result.content().getFirst().eventType());
    }

    @Test
    void rejectsInvalidTimeRangeAndReportsMissingIds() {
        assertThrows(IllegalArgumentException.class,
                () -> service.query(null, null, null, null, Instant.now(), Instant.EPOCH, 0, 10));
        assertThrows(AuditLogNotFoundException.class, () -> service.getById(UUID.randomUUID()));
    }

    private CreateAuditLogRequest event(String eventType, String actorId, String resourceType, String resourceId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source", "unit-test");
        return new CreateAuditLogRequest(eventType, actorId, resourceType, resourceId, payload);
    }
}
