# Tamper-evident audit log service

An in-memory, append-only audit log API. Each event includes the prior event hash and its own SHA-256 hash, creating a verifiable chain.

## Timestamp policy

The service assigns `timestamp` in UTC when an event is accepted. The write request deliberately does not accept a caller-provided timestamp, so clients cannot backdate or rewrite audit timing.

## API

All API routes are versioned under `/api/v1`.

| Operation | HTTP route |
| --- | --- |
| Create audit log | `POST /api/v1/audit-logs` |
| Get all audit logs | `GET /api/v1/audit-logs` |
| Verify integrity | `GET /api/v1/audit-logs/integrity` |

For assignment verification, the equivalent endpoint is also available at `GET /audit/verify`. The response includes `valid`, `checkedRecords`, `firstInconsistentRecordId`, and `violationType`. A broken link reports `PREVIOUS_HASH_MISMATCH`; modified event content reports `HASH_MISMATCH`.

The collection GET endpoints are query APIs. All filters are optional and combine with `AND` semantics:

```text
GET /api/v1/audit-logs?actorId=user-42&resourceType=customer&resourceId=customer-9&eventType=RECORD_UPDATED&from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z&page=0&size=25
```

`from` and `to` are inclusive UTC ISO-8601 timestamps. Pagination is zero-based; the default page size is 50 and the maximum is 100. The response contains `content`, `page`, `size`, `totalElements`, and `totalPages`.

Creating an event returns `201 Created`.

```json
{
  "eventType": "RECORD_UPDATED",
  "actorId": "user-42",
  "resourceType": "customer",
  "resourceId": "customer-9",
  "payload": { "changedFields": ["email"], "source": "admin-ui" }
}
```

There are intentionally no `PUT`, `PATCH`, or `DELETE` endpoints, and the repository has no mutation/removal operations.

Run locally with `./mvnw spring-boot:run` (or `mvnw.cmd spring-boot:run` on Windows).

## Retention, redaction, and export

`POST /audit/retention/archive?olderThanDays=90` soft-archives older records. Archive metadata is deliberately outside the event hash: records remain in storage and in verification, so legitimate archival does not break the chain. Archived events are omitted from normal queries.

`PATCH /audit/records/{id}/redactions` accepts JSON Pointer field paths, for example `{"fields":["/accountNumber","/person/ssn"]}`. At write time the service stores a SHA-256 commitment to the original canonical payload and hashes that commitment into the chain. Redaction replaces visible values with `[REDACTED]` while retaining the immutable commitment and original event hash; verification therefore remains intact. Unredacted payloads are additionally checked against their commitment.

Trade-off: after partial redaction, the commitment proves that an original payload existed but cannot prove each remaining visible field independently. A production system should use per-field Merkle commitments and cryptographic key destruction for stronger privacy guarantees. This in-memory implementation is a demonstrable policy model, not a substitute for encrypted persistent storage.

`GET /audit/export?actorId=user-42` (or `?resourceId=customer-9`) creates an export bundle. Every exported record includes its SHA-256 hash, predecessor hash, payload commitment, and redaction metadata, allowing a recipient to recompute each record hash and verify that its exported data has not changed.
