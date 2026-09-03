package com.auditlogservice;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.auditlogservice.repository.AuditLogRepository;
import com.auditlogservice.repository.impl.JdbcAuditLogRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-log-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.h2.console.enabled=false",
        "app.security.enabled=false"
})
@AutoConfigureMockMvc
class TamperEvidentAuditLogServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exposesProductionHealthProbeAndInfoEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void exposesOpenApiSpecificationAndSwaggerUi() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title", is("Tamper-Evident Audit Log Service API")))
                .andExpect(jsonPath("$.paths['/audit/verify'].get").exists())
                .andExpect(jsonPath("$.paths['/audit/export'].get").exists())
                .andExpect(jsonPath("$.paths['/audit/retention/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/audit/records/{id}/redactions'].patch").exists());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void usesJdbcPersistenceAndReturnsStoredDataThroughTheApi() throws Exception {
        String created = mockMvc.perform(post("/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("PERSISTED", "jdbc-user", "account", "jdbc-resource")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertTrue(auditLogRepository instanceof JdbcAuditLogRepository);
        String id = responseField(created, "id");
        assertTrue(auditLogRepository.findById(java.util.UUID.fromString(id)).isPresent());
        mockMvc.perform(get("/audit").param("actorId", "jdbc-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].resourceId", is("jdbc-resource")));
    }

    @Test
    void appendsAValidAuditEventWithServerAssignedTimestampAndHash() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("USER_LOGIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.eventType", is("USER_LOGIN")))
                .andExpect(jsonPath("$.actorId", is("user-42")))
                .andExpect(jsonPath("$.resourceType", is("session")))
                .andExpect(jsonPath("$.resourceId", is("session-9")))
                .andExpect(jsonPath("$.payload.ipAddress", is("203.0.113.1")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.hash").isNotEmpty())
                .andExpect(jsonPath("$.previousHash").isNotEmpty());
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"USER_LOGIN\",\"payload\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void rejectsPayloadThatIsNotAnObject() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"USER_LOGIN","actorId":"user-42","resourceType":"session",
                                 "resourceId":"session-9","payload":["not","an","object"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void returnsAConsistentErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Request body must contain valid JSON")));
    }

    @Test
    void rejectsAnInvalidTimeRange() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("from", "2030-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void linksSubsequentEventsAndReportsAnIntactChain() throws Exception {
        String firstResponse = mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("RECORD_UPDATED")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstHash = responseField(firstResponse, "hash");

        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("PERMISSION_GRANTED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousHash", is(firstHash)));

        mockMvc.perform(get("/api/v1/audit-logs/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.checkedRecords").isNumber());
    }

    @Test
    void exposesTheAssignmentVerifyEndpoint() throws Exception {
        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    @Test
    void detectsARecordModifiedDirectlyInTheH2DataStoreThroughTheVerifyApi() throws Exception {
        String created = mockMvc.perform(post("/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("DIRECT_DATABASE_TAMPER", "tamper-user", "account", "tamper-account")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = responseField(created, "id");
        String originalPayload = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM audit_log_entries WHERE id = ?", String.class, id);

        jdbcTemplate.update("UPDATE audit_log_entries SET payload_json = ? WHERE id = ?",
                "{\"ipAddress\":\"198.51.100.99\",\"successful\":true}", id);
        try {
            mockMvc.perform(get("/audit/verify"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(false)))
                    .andExpect(jsonPath("$.firstInconsistentRecordId", is(id)))
                    .andExpect(jsonPath("$.violationType", is("PAYLOAD_COMMITMENT_MISMATCH")));
        } finally {
            // Restore test state so this intentional tamper does not affect the remaining integration tests.
            jdbcTemplate.update("UPDATE audit_log_entries SET payload_json = ? WHERE id = ?", originalPayload, id);
        }
    }

    @Test
    void exposesNoUpdateOrDeleteRoute() throws Exception {
        mockMvc.perform(put("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON).content(validEvent("RECORD_UPDATED")))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/v1/audit-logs"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void supportsStandardCreateQueryAndVerifyMappings() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("USER_LOGIN")))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page", is(0)));
        mockMvc.perform(get("/api/v1/audit-logs/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    @Test
    void queriesEventsUsingCombinedFiltersAndPagination() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("RECORD_UPDATED", "query-user", "customer", "customer-9")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("actorId", "query-user")
                        .param("resourceType", "customer")
                        .param("resourceId", "customer-9")
                        .param("eventType", "RECORD_UPDATED")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2030-01-01T00:00:00Z")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].actorId", is("query-user")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)));
    }

    @Test
    void redactsFieldsThroughTheApiAndKeepsIntegrityValid() throws Exception {
        String created = mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"ACCOUNT_VIEWED","actorId":"redaction-user",
                                 "resourceType":"account","resourceId":"account-redact-1",
                                 "payload":{"accountNumber":"123456789","label":"checking"}}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = responseField(created, "id");

        mockMvc.perform(patch("/audit/records/{id}/redactions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[\"/accountNumber\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.accountNumber", is("[REDACTED]")))
                .andExpect(jsonPath("$.redactedFields[0]", is("/accountNumber")))
                .andExpect(jsonPath("$.fieldCommitments['/accountNumber']").isNotEmpty());

        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid", is(true)));
    }

    @Test
    void rejectsBadRedactionRequestsAndUnknownRecords() throws Exception {
        mockMvc.perform(patch("/audit/records/{id}/redactions", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":[\"/secret\"]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/audit/records/{id}/redactions", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportsAFilteredSelfContainedBundle() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEvent("EXPORTED", "bundle-user", "customer", "bundle-resource")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/audit/export").param("actorId", "bundle-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterType", is("actorId")))
                .andExpect(jsonPath("$.filterValue", is("bundle-user")))
                .andExpect(jsonPath("$.hashAlgorithm", is("SHA-256")))
                .andExpect(jsonPath("$.records.length()", is(1)))
                .andExpect(jsonPath("$.records[0].hash").isNotEmpty())
                .andExpect(jsonPath("$.records[0].fieldCommitments").isMap());

        mockMvc.perform(get("/audit/export"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audit/export").param("actorId", "a").param("resourceId", "r"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesAndExecutesRetentionEndpoint() throws Exception {
        mockMvc.perform(post("/audit/retention/archive").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cutoff").isNotEmpty())
                .andExpect(jsonPath("$.archivedRecords").isNumber());
        mockMvc.perform(post("/audit/retention/archive").param("olderThanDays", "0"))
                .andExpect(status().isBadRequest());
    }

    private String validEvent(String eventType) {
        return validEvent(eventType, "user-42", "session", "session-9");
    }

    private String validEvent(String eventType, String actorId, String resourceType, String resourceId) {
        return """
                {
                  "eventType": "%s",
                  "actorId": "%s",
                  "resourceType": "%s",
                  "resourceId": "%s",
                  "payload": { "ipAddress": "203.0.113.1", "successful": true }
                }
                """.formatted(eventType, actorId, resourceType, resourceId);
    }

    private String responseField(String response, String fieldName) throws Exception {
        JsonNode document = objectMapper.readTree(response);
        return document.get(fieldName).asString();
    }
}
