package com.auditlogservice.service.impl;

import com.auditlogservice.dto.AuditLogResponse;
import com.auditlogservice.dto.AuditLogPageResponse;
import com.auditlogservice.dto.CreateAuditLogRequest;
import com.auditlogservice.dto.IntegrityVerificationResponse;
import com.auditlogservice.dto.RetentionResponse;
import com.auditlogservice.dto.AuditLogExportBundle;
import com.auditlogservice.dto.ExportChainLink;
import com.auditlogservice.exception.AuditLogNotFoundException;
import com.auditlogservice.model.AuditLogEntry;
import com.auditlogservice.repository.AuditLogRepository;
import com.auditlogservice.service.AuditLogService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Builds a SHA-256 hash chain over events stored by the repository. */
@Service
public class HashingAuditLogService implements AuditLogService {
    private static final String GENESIS_HASH = "GENESIS";

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final KeyPair signingKeyPair;

    @Autowired
    public HashingAuditLogService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    HashingAuditLogService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
        this.signingKeyPair = generateSigningKeyPair();
    }

    @Override
    public synchronized AuditLogResponse append(CreateAuditLogRequest request) {
        String previousHash = repository.findLatest().map(AuditLogEntry::hash).orElse(GENESIS_HASH);
        UUID id = UUID.randomUUID();
        // H2 and PostgreSQL both preserve microseconds; normalize before hashing to avoid JDBC precision drift.
        Instant timestamp = clock.instant().truncatedTo(ChronoUnit.MICROS);
        JsonNode payload = request.payload().deepCopy();
        CommitmentData commitmentData = createFieldCommitments(payload);
        Map<String, String> fieldCommitments = commitmentData.commitments();
        String payloadCommitment = commitmentRoot(fieldCommitments);
        String hash = calculateHash(id, request.eventType(), request.actorId(), request.resourceType(),
                request.resourceId(), payloadCommitment, timestamp, previousHash);
        AuditLogEntry entry = new AuditLogEntry(id, request.eventType(), request.actorId(), request.resourceType(),
                request.resourceId(), payload, timestamp, previousHash, hash, payloadCommitment,
                fieldCommitments, commitmentData.salts(), Set.of(), null);
        return AuditLogResponse.from(repository.append(entry));
    }

    @Override
    public AuditLogResponse getById(UUID id) {
        return repository.findById(id).map(AuditLogResponse::from)
                .orElseThrow(() -> new AuditLogNotFoundException(id));
    }

    @Override
    public AuditLogPageResponse query(String actorId, String resourceType, String resourceId, String eventType,
            Instant from, Instant to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        List<AuditLogResponse> filtered = repository.findAll().stream()
                .filter(entry -> entry.archivedAt() == null)
                .filter(entry -> actorId == null || actorId.equals(entry.actorId()))
                .filter(entry -> resourceType == null || resourceType.equals(entry.resourceType()))
                .filter(entry -> resourceId == null || resourceId.equals(entry.resourceId()))
                .filter(entry -> eventType == null || eventType.equals(entry.eventType()))
                .filter(entry -> from == null || !entry.timestamp().isBefore(from))
                .filter(entry -> to == null || !entry.timestamp().isAfter(to))
                .map(AuditLogResponse::from)
                .toList();
        long totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        long requestedStart = (long) page * size;
        int start = requestedStart >= filtered.size() ? filtered.size() : (int) requestedStart;
        int end = Math.min(start + size, filtered.size());
        return new AuditLogPageResponse(filtered.subList(start, end), page, size, totalElements, totalPages);
    }

    @Override
    public IntegrityVerificationResponse verifyIntegrity() {
        List<AuditLogEntry> entries = repository.findAll();
        String expectedPreviousHash = GENESIS_HASH;
        for (AuditLogEntry entry : entries) {
            String expectedHash = calculateHash(entry.id(), entry.eventType(), entry.actorId(), entry.resourceType(),
                    entry.resourceId(), entry.payloadCommitment(), entry.timestamp(), expectedPreviousHash);
            if (!expectedPreviousHash.equals(entry.previousHash())) {
                return new IntegrityVerificationResponse(false, entries.size(), entry.id(), "PREVIOUS_HASH_MISMATCH",
                        "Event does not link to the hash of its predecessor");
            }
            if (!expectedHash.equals(entry.hash())) {
                return new IntegrityVerificationResponse(false, entries.size(), entry.id(), "HASH_MISMATCH",
                        "Event contents do not match its stored hash");
            }
            if (!verifyPayload(entry)) {
                return new IntegrityVerificationResponse(false, entries.size(), entry.id(), "PAYLOAD_COMMITMENT_MISMATCH",
                        "Payload and redaction metadata do not match the committed original payload");
            }
            expectedPreviousHash = entry.hash();
        }
        return new IntegrityVerificationResponse(true, entries.size(), null, null, "All audit events are intact");
    }

    private String calculateHash(UUID id, String eventType, String actorId, String resourceType,
            String resourceId, String payloadCommitment, Instant timestamp, String previousHash) {
        String material = "{\"actorId\":" + jsonString(actorId)
                + ",\"eventType\":" + jsonString(eventType)
                + ",\"id\":" + jsonString(id.toString())
                + ",\"payloadCommitment\":" + jsonString(payloadCommitment)
                + ",\"previousHash\":" + jsonString(previousHash)
                + ",\"resourceId\":" + jsonString(resourceId)
                + ",\"resourceType\":" + jsonString(resourceType)
                + ",\"timestamp\":" + jsonString(timestamp.toString()) + "}";
        return sha256(material);
    }

    @Override
    public AuditLogResponse redact(UUID id, List<String> fields) {
        AuditLogEntry entry = repository.findById(id).orElseThrow(() -> new AuditLogNotFoundException(id));
        JsonNode payload = entry.payload().deepCopy();
        java.util.HashSet<String> redacted = new java.util.HashSet<>(entry.redactedFields());
        Map<String, String> salts = new HashMap<>(entry.fieldSalts());
        for (String field : fields) {
            if (!entry.fieldCommitments().containsKey(field)) {
                throw new IllegalArgumentException("Redaction path must identify a scalar payload field: " + field);
            }
            redactField(payload, field);
            redacted.add(field);
            salts.remove(field); // Cryptographic erasure: prevents offline guessing of the removed value.
        }
        AuditLogEntry updated = new AuditLogEntry(entry.id(), entry.eventType(), entry.actorId(), entry.resourceType(),
                entry.resourceId(), payload, entry.timestamp(), entry.previousHash(), entry.hash(), entry.payloadCommitment(),
                entry.fieldCommitments(), Map.copyOf(salts), Set.copyOf(redacted), entry.archivedAt());
        return AuditLogResponse.from(repository.replace(updated));
    }

    @Override
    public RetentionResponse archiveOlderThan(long olderThanDays) {
        Instant cutoff = clock.instant().minus(java.time.Duration.ofDays(olderThanDays));
        long archived = 0;
        for (AuditLogEntry entry : repository.findAll()) {
            if (entry.archivedAt() == null && entry.timestamp().isBefore(cutoff)) {
                repository.replace(new AuditLogEntry(entry.id(), entry.eventType(), entry.actorId(), entry.resourceType(),
                        entry.resourceId(), entry.payload(), entry.timestamp(), entry.previousHash(), entry.hash(),
                        entry.payloadCommitment(), entry.fieldCommitments(), entry.fieldSalts(),
                        entry.redactedFields(), clock.instant()));
                archived++;
            }
        }
        return new RetentionResponse(cutoff, archived);
    }

    @Override
    public AuditLogExportBundle export(String actorId, String resourceId) {
        if ((actorId == null && resourceId == null) || (actorId != null && resourceId != null)) {
            throw new IllegalArgumentException("Specify exactly one of actorId or resourceId");
        }
        List<AuditLogResponse> records = repository.findAll().stream()
                .filter(entry -> actorId != null ? actorId.equals(entry.actorId()) : resourceId.equals(entry.resourceId()))
                .map(AuditLogResponse::from).toList();
        List<ExportChainLink> chainProof = repository.findAll().stream().map(ExportChainLink::from).toList();
        Instant exportedAt = clock.instant();
        String filterType = actorId == null ? "resourceId" : "actorId";
        String filterValue = actorId == null ? resourceId : actorId;
        String chainHeadHash = chainProof.isEmpty() ? GENESIS_HASH : chainProof.getLast().hash();
        ExportUnsignedData unsigned = new ExportUnsignedData(exportedAt, filterType, filterValue, "SHA-256",
                chainHeadHash, records, chainProof);
        String bundleDigest = sha256(canonicalJson(objectMapper.valueToTree(unsigned)));
        String publicKey = Base64.getEncoder().encodeToString(signingKeyPair.getPublic().getEncoded());
        String keyId = sha256(publicKey).substring(0, 16);
        return new AuditLogExportBundle(exportedAt, filterType, filterValue, "SHA-256", chainHeadHash,
                records, chainProof, bundleDigest, "Ed25519", publicKey, keyId, sign(bundleDigest));
    }

    private void redactField(JsonNode payload, String pointer) {
        if (!pointer.startsWith("/") || pointer.length() == 1) {
            throw new IllegalArgumentException("Redaction fields must be JSON Pointer paths such as /accountNumber");
        }
        String[] segments = pointer.substring(1).split("/");
        JsonNode parent = payload;
        for (int index = 0; index < segments.length - 1; index++) {
            parent = parent.get(unescape(segments[index]));
            if (parent == null) throw new IllegalArgumentException("Redaction path does not exist: " + pointer);
        }
        String field = unescape(segments[segments.length - 1]);
        if (parent instanceof ObjectNode object && object.has(field)) object.put(field, "[REDACTED]");
        else if (parent instanceof ArrayNode array) {
            try { array.set(Integer.parseInt(field), objectMapper.valueToTree("[REDACTED]")); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("Redaction path does not exist: " + pointer); }
        } else throw new IllegalArgumentException("Redaction path does not exist: " + pointer);
    }

    private String unescape(String segment) { return segment.replace("~1", "/").replace("~0", "~"); }

    private CommitmentData createFieldCommitments(JsonNode payload) {
        Map<String, String> commitments = new LinkedHashMap<>();
        Map<String, String> salts = new LinkedHashMap<>();
        collectLeafCommitments(payload, "", commitments, salts);
        return new CommitmentData(Map.copyOf(commitments), Map.copyOf(salts));
    }

    private void collectLeafCommitments(JsonNode node, String pointer, Map<String, String> commitments,
            Map<String, String> salts) {
        if (node.isObject()) {
            node.properties().stream().sorted(Map.Entry.comparingByKey()).forEach(field ->
                    collectLeafCommitments(field.getValue(), pointer + "/" + escape(field.getKey()), commitments, salts));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                collectLeafCommitments(node.get(index), pointer + "/" + index, commitments, salts);
            }
        } else {
            byte[] saltBytes = new byte[16];
            secureRandom.nextBytes(saltBytes);
            String salt = Base64.getEncoder().encodeToString(saltBytes);
            salts.put(pointer, salt);
            commitments.put(pointer, fieldCommitment(salt, node));
        }
    }

    private boolean verifyPayload(AuditLogEntry entry) {
        if (!commitmentRoot(entry.fieldCommitments()).equals(entry.payloadCommitment())) return false;
        if (!entry.fieldCommitments().keySet().containsAll(entry.redactedFields())) return false;
        if (!collectLeafPaths(entry.payload()).equals(entry.fieldCommitments().keySet())) return false;
        Set<String> expectedSaltPaths = entry.fieldCommitments().keySet().stream()
                .filter(path -> !entry.redactedFields().contains(path)).collect(Collectors.toSet());
        if (!entry.fieldSalts().keySet().equals(expectedSaltPaths)) return false;
        for (Map.Entry<String, String> field : entry.fieldCommitments().entrySet()) {
            JsonNode value = entry.payload().at(field.getKey());
            if (value.isMissingNode()) return false;
            if (entry.redactedFields().contains(field.getKey())) {
                if (!value.isTextual() || !"[REDACTED]".equals(value.asText())) return false;
            } else if (!fieldCommitment(entry.fieldSalts().get(field.getKey()), value).equals(field.getValue())) {
                return false;
            }
        }
        return true;
    }

    private String commitmentRoot(Map<String, String> commitments) {
        String material = commitments.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> jsonString(entry.getKey()) + ":" + jsonString(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        return sha256(material);
    }

    private String escape(String segment) { return segment.replace("~", "~0").replace("/", "~1"); }

    private Set<String> collectLeafPaths(JsonNode payload) {
        Set<String> paths = new java.util.HashSet<>();
        collectLeafPaths(payload, "", paths);
        return paths;
    }

    private void collectLeafPaths(JsonNode node, String pointer, Set<String> paths) {
        if (node.isObject()) {
            node.properties().forEach(field -> collectLeafPaths(
                    field.getValue(), pointer + "/" + escape(field.getKey()), paths));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) collectLeafPaths(node.get(index), pointer + "/" + index, paths);
        } else paths.add(pointer);
    }

    private String fieldCommitment(String salt, JsonNode value) {
        return sha256(salt + ":" + canonicalJson(value));
    }

    private KeyPair generateSigningKeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Ed25519 is unavailable", exception);
        }
    }

    private String sign(String bundleDigest) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(signingKeyPair.getPrivate());
            signer.update(bundleDigest.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign export bundle", exception);
        }
    }

    private record CommitmentData(Map<String, String> commitments, Map<String, String> salts) {}

    private record ExportUnsignedData(Instant exportedAt, String filterType, String filterValue,
            String hashAlgorithm, String chainHeadHash, List<AuditLogResponse> records,
            List<ExportChainLink> chainProof) {}

    private String sha256(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private String canonicalJson(JsonNode node) {
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

    private String jsonString(String value) {
        return objectMapper.valueToTree(value).toString();
    }
}
