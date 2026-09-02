package com.auditlogservice.dto;

import java.util.UUID;

/** Result of walking the complete audit hash chain in insertion order. */
public record IntegrityVerificationResponse(
        boolean valid,
        long checkedRecords,
        UUID firstInconsistentRecordId,
        String violationType,
        String message) {
}
