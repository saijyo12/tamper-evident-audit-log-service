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
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.stream.Collectors;
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
        assertEquals("PAYLOAD_COMMITMENT_MISMATCH", verification.violationType());
    }

    @Test
    void identifiesTheFirstBrokenPreviousHashLink() {
        service.append(event("USER_LOGIN", "actor-1", "session", "s-1"));
        AuditLogResponse second = service.append(event("RECORD_UPDATED", "actor-1", "session", "s-1"));
        AuditLogEntry storedSecond = repository.findAll().get(1);
        repository.append(new AuditLogEntry(storedSecond.id(), storedSecond.eventType(), storedSecond.actorId(),
                storedSecond.resourceType(), storedSecond.resourceId(), storedSecond.payload(), storedSecond.timestamp(),
                "not-the-previous-hash", storedSecond.hash(), storedSecond.payloadCommitment(),
                storedSecond.fieldCommitments(), storedSecond.fieldSalts(),
                storedSecond.redactedFields(), storedSecond.archivedAt()));

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

    @Test
    void redactsNestedAndArrayFieldsWithoutBreakingIntegrity() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("accountNumber", "123456789");
        payload.putObject("person").put("ssn", "111-22-3333").put("name", "Ada");
        payload.putArray("codes").add("secret").add("public");
        AuditLogResponse original = service.append(new CreateAuditLogRequest(
                "VIEWED", "actor-1", "customer", "c-1", payload));

        AuditLogResponse redacted = service.redact(original.id(), List.of("/accountNumber", "/person/ssn", "/codes/0"));

        assertEquals("[REDACTED]", redacted.payload().get("accountNumber").asText());
        assertEquals("[REDACTED]", redacted.payload().at("/person/ssn").asText());
        assertEquals("[REDACTED]", redacted.payload().at("/codes/0").asText());
        assertEquals(original.hash(), redacted.hash());
        assertEquals(original.payloadCommitment(), redacted.payloadCommitment());
        assertTrue(redacted.fieldCommitments().containsKey("/person/ssn"));
        assertFalse(redacted.fieldSalts().containsKey("/person/ssn"));
        assertFalse(redacted.fieldSalts().containsKey("/accountNumber"));
        assertTrue(redacted.fieldSalts().containsKey("/person/name"));
        assertTrue(service.verifyIntegrity().valid());
    }

    @Test
    void detectsVisiblePayloadTamperingAfterAnotherFieldWasRedacted() {
        ObjectNode payload = objectMapper.createObjectNode().put("secret", "hide-me").put("visible", "keep-me");
        AuditLogResponse original = service.append(new CreateAuditLogRequest(
                "VIEWED", "actor-1", "customer", "c-1", payload));
        service.redact(original.id(), List.of("/secret"));
        ((ObjectNode) repository.findById(original.id()).orElseThrow().payload()).put("visible", "tampered");

        IntegrityVerificationResponse result = service.verifyIntegrity();

        assertFalse(result.valid());
        assertEquals("PAYLOAD_COMMITMENT_MISMATCH", result.violationType());
    }

    @Test
    void rejectsInvalidOrMissingRedactionPaths() {
        AuditLogResponse entry = service.append(event("VIEWED", "actor-1", "customer", "c-1"));

        assertThrows(IllegalArgumentException.class, () -> service.redact(entry.id(), List.of("source")));
        assertThrows(IllegalArgumentException.class, () -> service.redact(entry.id(), List.of("/missing")));
    }

    @Test
    void archivesOldRecordsOmitsThemFromQueriesAndStillVerifiesTheChain() {
        Instant oldTime = Instant.parse("2026-01-01T00:00:00Z");
        HashingAuditLogService oldService = new HashingAuditLogService(repository, objectMapper,
                Clock.fixed(oldTime, ZoneOffset.UTC));
        AuditLogResponse response = oldService.append(event("OLD", "actor-old", "customer", "old-1"));
        service = new HashingAuditLogService(repository, objectMapper,
                Clock.fixed(oldTime.plus(Duration.ofDays(31)), ZoneOffset.UTC));

        assertEquals(1, service.archiveOlderThan(30).archivedRecords());
        assertNotNull(repository.findById(response.id()).orElseThrow().archivedAt());
        assertEquals(0, service.query("actor-old", null, null, null, null, null, 0, 10).totalElements());
        assertTrue(service.verifyIntegrity().valid());
    }

    @Test
    void exportsByExactlyOneFilterIncludingArchivedRecordsAndVerificationMetadata() throws Exception {
        Instant oldTime = Instant.parse("2026-01-01T00:00:00Z");
        service = new HashingAuditLogService(repository, objectMapper, Clock.fixed(oldTime, ZoneOffset.UTC));
        AuditLogResponse matching = service.append(event("VIEWED", "export-actor", "customer", "resource-1"));
        service.append(event("VIEWED", "other-actor", "customer", "resource-2"));
        service = new HashingAuditLogService(repository, objectMapper,
                Clock.fixed(oldTime.plus(Duration.ofDays(31)), ZoneOffset.UTC));
        service.archiveOlderThan(30);

        var byActor = service.export("export-actor", null);
        var byResource = service.export(null, "resource-1");

        assertEquals("actorId", byActor.filterType());
        assertEquals(1, byActor.records().size());
        assertEquals(matching.hash(), byActor.records().getFirst().hash());
        assertNotNull(byActor.records().getFirst().archivedAt());
        assertFalse(byActor.records().getFirst().fieldCommitments().isEmpty());
        assertEquals(2, byActor.chainProof().size());
        assertEquals(byActor.chainProof().getLast().hash(), byActor.chainHeadHash());
        assertTrue(verifyBundleDigestAndSignature(byActor));
        assertEquals(1, byResource.records().size());
        assertThrows(IllegalArgumentException.class, () -> service.export(null, null));
        assertThrows(IllegalArgumentException.class, () -> service.export("a", "r"));
    }

    @Test
    void exportProofShowsEveryMatchingRecordAndDetectsBundleChanges() throws Exception {
        service.append(event("FIRST", "target", "customer", "r-1"));
        service.append(event("UNRELATED", "other", "customer", "r-2"));
        service.append(event("LAST", "target", "customer", "r-3"));

        var bundle = service.export("target", null);

        assertEquals(2, bundle.records().size());
        assertEquals(3, bundle.chainProof().size());
        long matchesInProof = bundle.chainProof().stream().filter(link -> "target".equals(link.actorId())).count();
        assertEquals(bundle.records().size(), matchesInProof);
        assertTrue(verifyBundleDigestAndSignature(bundle));

        var tamperedRecords = new java.util.ArrayList<>(bundle.records());
        tamperedRecords.removeFirst();
        var tampered = new com.auditlogservice.dto.AuditLogExportBundle(bundle.exportedAt(), bundle.filterType(),
                bundle.filterValue(), bundle.hashAlgorithm(), bundle.chainHeadHash(), List.copyOf(tamperedRecords),
                bundle.chainProof(), bundle.bundleDigest(), bundle.signatureAlgorithm(), bundle.signingPublicKey(),
                bundle.signingKeyId(), bundle.signature());
        assertFalse(verifyBundleDigestAndSignature(tampered));
    }

    private boolean verifyBundleDigestAndSignature(com.auditlogservice.dto.AuditLogExportBundle bundle) throws Exception {
        ObjectNode unsigned = (ObjectNode) objectMapper.valueToTree(bundle);
        unsigned.remove(List.of("bundleDigest", "signatureAlgorithm", "signingPublicKey", "signingKeyId", "signature"));
        String digest = sha256(canonicalJson(unsigned));
        if (!digest.equals(bundle.bundleDigest())) return false;
        String expectedKeyId = sha256(bundle.signingPublicKey()).substring(0, 16);
        if (!expectedKeyId.equals(bundle.signingKeyId())) return false;
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(bundle.signingPublicKey()))));
        verifier.update(bundle.bundleDigest().getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(bundle.signature()));
    }

    private String canonicalJson(tools.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream().sorted(Map.Entry.comparingByKey())
                    .map(field -> objectMapper.valueToTree(field.getKey()).toString() + ":" + canonicalJson(field.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (node.isArray()) {
            return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                    .map(this::canonicalJson).collect(Collectors.joining(",", "[", "]"));
        }
        return node.toString();
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private CreateAuditLogRequest event(String eventType, String actorId, String resourceType, String resourceId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source", "unit-test");
        return new CreateAuditLogRequest(eventType, actorId, resourceType, resourceId, payload);
    }
}
