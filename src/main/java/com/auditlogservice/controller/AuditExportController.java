package com.auditlogservice.controller;

import com.auditlogservice.dto.AuditLogExportBundle;
import com.auditlogservice.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/audit/export")
@Tag(name = "Export", description = "Signed, verifiable audit-record export bundles")
public class AuditExportController {
    private final AuditLogService auditLogService;
    public AuditExportController(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @GetMapping
    @Operation(summary = "Export a verifiable audit bundle", description = "Exports all records for exactly one actorId or resourceId, with chain proof and signature metadata.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Signed export bundle"),
            @ApiResponse(responseCode = "400", description = "Specify exactly one filter")})
    public ResponseEntity<AuditLogExportBundle> export(@RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceId) {
        return ResponseEntity.ok(auditLogService.export(actorId, resourceId));
    }

}
