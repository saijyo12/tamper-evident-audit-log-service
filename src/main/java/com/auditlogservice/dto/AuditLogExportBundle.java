package com.auditlogservice.dto;

import java.time.Instant;
import java.util.List;

/** Self-contained export: each record has its hash and predecessor hash for independent recomputation. */
public record AuditLogExportBundle(
        Instant exportedAt,
        String filterType,
        String filterValue,
        String hashAlgorithm,
        String chainHeadHash,
        List<AuditLogResponse> records,
        List<ExportChainLink> chainProof,
        String bundleDigest,
        String signatureAlgorithm,
        String signingPublicKey,
        String signingKeyId,
        String signature) {
}
