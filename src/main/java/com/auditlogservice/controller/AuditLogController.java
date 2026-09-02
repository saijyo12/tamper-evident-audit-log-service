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

/** HTTP surface intentionally exposes append and read operations only. */
@RestController
@Validated
@RequestMapping({"/api/v1/audit-logs", "/audit"})
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public ResponseEntity<AuditLogResponse> createAuditLog(@Valid @RequestBody CreateAuditLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditLogService.append(request));
    }

    @GetMapping
    public ResponseEntity<AuditLogPageResponse> queryAuditLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(auditLogService.query(actorId, resourceType, resourceId, eventType, from, to, page, size));
    }

    @GetMapping({"/integrity", "/verify"})
    public ResponseEntity<IntegrityVerificationResponse> verifyAuditLogIntegrity() {
        return ResponseEntity.ok(auditLogService.verifyIntegrity());
    }
}
