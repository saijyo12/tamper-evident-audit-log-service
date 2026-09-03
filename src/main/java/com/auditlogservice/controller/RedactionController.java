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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/audit/records")
@Tag(name = "Redaction", description = "Privacy-preserving redaction of committed scalar payload fields")
public class RedactionController {
    private final AuditLogService auditLogService;
    public RedactionController(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @PatchMapping("/{id}/redactions")
    @Operation(summary = "Redact payload fields", description = "Replaces scalar JSON Pointer values with a redaction marker while preserving the original chain commitment.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Fields redacted"),
            @ApiResponse(responseCode = "400", description = "Invalid JSON Pointer or request"),
            @ApiResponse(responseCode = "404", description = "Record not found")})
    public ResponseEntity<AuditLogResponse> redact(@PathVariable UUID id,
            @Valid @RequestBody RedactAuditLogRequest request) {
        return ResponseEntity.ok(auditLogService.redact(id, request.fields()));
    }
}
