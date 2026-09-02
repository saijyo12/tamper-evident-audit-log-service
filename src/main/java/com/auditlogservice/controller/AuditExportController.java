package com.auditlogservice.controller;

import com.auditlogservice.dto.AuditLogExportBundle;
import com.auditlogservice.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit/export")
public class AuditExportController {
    private final AuditLogService auditLogService;
    public AuditExportController(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @GetMapping
    public ResponseEntity<AuditLogExportBundle> export(@RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceId) {
        return ResponseEntity.ok(auditLogService.export(actorId, resourceId));
    }
}
