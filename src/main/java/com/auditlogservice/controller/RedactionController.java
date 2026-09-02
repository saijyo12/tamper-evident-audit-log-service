package com.auditlogservice.controller;

import com.auditlogservice.dto.AuditLogResponse;
import com.auditlogservice.dto.RedactAuditLogRequest;
import com.auditlogservice.service.AuditLogService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit/records")
public class RedactionController {
    private final AuditLogService auditLogService;
    public RedactionController(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @PatchMapping("/{id}/redactions")
    public ResponseEntity<AuditLogResponse> redact(@PathVariable UUID id,
            @Valid @RequestBody RedactAuditLogRequest request) {
        return ResponseEntity.ok(auditLogService.redact(id, request.fields()));
    }
}
