package com.auditlogservice;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TamperEvidentAuditLogServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
