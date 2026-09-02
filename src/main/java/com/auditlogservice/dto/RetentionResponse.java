package com.auditlogservice.dto;

import java.time.Instant;

public record RetentionResponse(Instant cutoff, long archivedRecords) {
}
