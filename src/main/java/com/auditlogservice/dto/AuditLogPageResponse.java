package com.auditlogservice.dto;

import java.util.List;

/** A stable, pagination-aware response for audit-log queries. */
public record AuditLogPageResponse(
        List<AuditLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
