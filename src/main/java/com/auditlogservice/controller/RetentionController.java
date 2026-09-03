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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Policy-controlled soft archival; archived records remain part of chain verification. */
@RestController
@Validated
@RequestMapping("/audit/retention")
@Tag(name = "Retention", description = "Policy-controlled soft archival of audit records")
public class RetentionController {
    private final AuditLogService auditLogService;
    public RetentionController(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @PostMapping("/archive")
    @Operation(summary = "Archive records older than a policy window", description = "Soft-archives qualifying records without removing them from chain verification.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Archive policy applied"),
            @ApiResponse(responseCode = "400", description = "Window must be at least one day")})
    public ResponseEntity<RetentionResponse> archive(@RequestParam @Min(1) long olderThanDays) {
        return ResponseEntity.ok(auditLogService.archiveOlderThan(olderThanDays));
    }
}
