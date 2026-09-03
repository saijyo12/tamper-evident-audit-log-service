package com.auditlogservice.controller;

import com.auditlogservice.dto.AuditLogResponse;
import com.auditlogservice.dto.AuditLogPageResponse;
import com.auditlogservice.dto.CreateAuditLogRequest;
import com.auditlogservice.dto.IntegrityVerificationResponse;
import com.auditlogservice.service.AuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** HTTP surface intentionally exposes append and read operations only. */
@RestController
@Validated
@RequestMapping({"/api/v1/audit-logs", "/audit"})
@Tag(name = "Audit records", description = "Create, query, and verify tamper-evident audit records")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping
    @Operation(summary = "Append an audit event", description = "Stores a server-timestamped event and links it to the preceding event hash.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Event appended"),
            @ApiResponse(responseCode = "400", description = "Invalid event payload")})
    public ResponseEntity<AuditLogResponse> createAuditLog(@Valid @RequestBody CreateAuditLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditLogService.append(request));
    }

    @GetMapping
    @Operation(summary = "Query audit events", description = "Filters non-archived events using any combination of supported filters and returns a page.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page of matching events"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or pagination value")})
    public ResponseEntity<AuditLogPageResponse> queryAuditLogs(
            @Parameter(description = "Actor that caused the event") @RequestParam(required = false) String actorId,
            @Parameter(description = "Affected resource type") @RequestParam(required = false) String resourceType,
            @Parameter(description = "Affected resource identifier") @RequestParam(required = false) String resourceId,
            @Parameter(description = "Audit event type") @RequestParam(required = false) String eventType,
            @Parameter(description = "Inclusive UTC start timestamp") @RequestParam(required = false) Instant from,
            @Parameter(description = "Inclusive UTC end timestamp") @RequestParam(required = false) Instant to,
            @Parameter(description = "Zero-based result page") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 100") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(auditLogService.query(actorId, resourceType, resourceId, eventType, from, to, page, size));
    }

    @GetMapping({"/integrity", "/verify"})
    @Operation(summary = "Verify the audit hash chain", description = "Walks every record, including archived records, and reports the first integrity violation.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Integrity verification result"))
    public ResponseEntity<IntegrityVerificationResponse> verifyAuditLogIntegrity() {
        return ResponseEntity.ok(auditLogService.verifyIntegrity());
    }
}
