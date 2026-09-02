package com.auditlogservice.controller;

import com.auditlogservice.dto.RetentionResponse;
import com.auditlogservice.service.AuditLogService;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Policy-controlled soft archival; archived records remain part of chain verification. */
@RestController
@Validated
@RequestMapping("/audit/retention")
public class RetentionController {
    private final AuditLogService auditLogService;
    public RetentionController(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @PostMapping("/archive")
    public ResponseEntity<RetentionResponse> archive(@RequestParam @Min(1) long olderThanDays) {
        return ResponseEntity.ok(auditLogService.archiveOlderThan(olderThanDays));
    }
}
