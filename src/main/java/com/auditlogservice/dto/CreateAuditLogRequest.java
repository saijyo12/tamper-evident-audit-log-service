package com.auditlogservice.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

/** The timestamp is deliberately omitted: it is assigned by the server in UTC. */
public record CreateAuditLogRequest(
        @NotBlank String eventType,
        @NotBlank String actorId,
        @NotBlank String resourceType,
        @NotBlank String resourceId,
        @NotNull JsonNode payload) {

    @AssertTrue(message = "must be a JSON object")
    public boolean isPayloadObject() {
        return payload != null && payload.isObject();
    }
}
