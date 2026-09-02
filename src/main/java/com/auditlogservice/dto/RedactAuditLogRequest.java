package com.auditlogservice.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** JSON Pointer paths to redact, for example: /accountNumber or /person/ssn. */
public record RedactAuditLogRequest(@NotEmpty List<String> fields) {
}
