package com.auditlogservice.repository.impl;

import com.auditlogservice.model.AuditLogEntry;
import com.auditlogservice.repository.AuditLogRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Ordered JDBC storage used by both the local H2 and production PostgreSQL profiles. */
@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {
    private static final String SELECT_COLUMNS = """
            SELECT id, event_type, actor_id, resource_type, resource_id, payload_json,
                   event_timestamp, previous_hash, event_hash, payload_commitment,
                   field_commitments_json, field_salts_json, redacted_fields_json, archived_at
              FROM audit_log_entries
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<AuditLogEntry> rowMapper = this::mapRow;

    public JdbcAuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditLogEntry append(AuditLogEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO audit_log_entries
                    (id, event_type, actor_id, resource_type, resource_id, payload_json,
                     event_timestamp, previous_hash, event_hash, payload_commitment,
                     field_commitments_json, field_salts_json, redacted_fields_json, archived_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entry.id().toString(), entry.eventType(), entry.actorId(), entry.resourceType(),
                entry.resourceId(), json(entry.payload()), entry.timestamp().atOffset(ZoneOffset.UTC),
                entry.previousHash(), entry.hash(), entry.payloadCommitment(), json(entry.fieldCommitments()),
                json(entry.fieldSalts()), json(entry.redactedFields()),
                entry.archivedAt() == null ? null : entry.archivedAt().atOffset(ZoneOffset.UTC));
        return entry;
    }

    @Override
    public AuditLogEntry replace(AuditLogEntry entry) {
        int updated = jdbcTemplate.update("""
                UPDATE audit_log_entries
                   SET event_type = ?, actor_id = ?, resource_type = ?, resource_id = ?, payload_json = ?,
                       event_timestamp = ?, previous_hash = ?, event_hash = ?, payload_commitment = ?,
                       field_commitments_json = ?, field_salts_json = ?, redacted_fields_json = ?, archived_at = ?
                 WHERE id = ?
                """, entry.eventType(), entry.actorId(), entry.resourceType(), entry.resourceId(),
                json(entry.payload()), entry.timestamp().atOffset(ZoneOffset.UTC), entry.previousHash(), entry.hash(),
                entry.payloadCommitment(), json(entry.fieldCommitments()), json(entry.fieldSalts()),
                json(entry.redactedFields()), entry.archivedAt() == null ? null : entry.archivedAt().atOffset(ZoneOffset.UTC),
                entry.id().toString());
        if (updated == 0) throw new IllegalArgumentException("Cannot replace an audit event that does not exist");
        return entry;
    }

    @Override
    public Optional<AuditLogEntry> findById(UUID id) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", rowMapper, id.toString()).stream().findFirst();
    }

    @Override
    public List<AuditLogEntry> findAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY chain_sequence", rowMapper);
    }

    @Override
    public Optional<AuditLogEntry> findLatest() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY chain_sequence DESC FETCH FIRST 1 ROW ONLY", rowMapper)
                .stream().findFirst();
    }

    private AuditLogEntry mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime archivedAt = resultSet.getObject("archived_at", OffsetDateTime.class);
        return new AuditLogEntry(UUID.fromString(resultSet.getString("id")), resultSet.getString("event_type"),
                resultSet.getString("actor_id"), resultSet.getString("resource_type"),
                resultSet.getString("resource_id"), readTree(resultSet.getString("payload_json")),
                resultSet.getObject("event_timestamp", OffsetDateTime.class).toInstant(),
                resultSet.getString("previous_hash"), resultSet.getString("event_hash"),
                resultSet.getString("payload_commitment"),
                read(resultSet.getString("field_commitments_json"), new TypeReference<Map<String, String>>() {}),
                read(resultSet.getString("field_salts_json"), new TypeReference<Map<String, String>>() {}),
                read(resultSet.getString("redacted_fields_json"), new TypeReference<Set<String>>() {}),
                archivedAt == null ? null : archivedAt.toInstant());
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readTree(String value) {
        return objectMapper.readTree(value);
    }

    private <T> T read(String value, TypeReference<T> type) {
        return objectMapper.readValue(value, type);
    }
}
