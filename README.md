# Tamper-evident audit log service

A persistent, append-only audit log API. Each event includes the prior event hash and its own SHA-256 hash, creating a verifiable chain. The local profile uses file-backed H2; production is configured for PostgreSQL.

## Timestamp policy

The service assigns `timestamp` in UTC when an event is accepted. The write request deliberately does not accept a caller-provided timestamp, so clients cannot backdate or rewrite audit timing.

## API

All API routes are versioned under `/api/v1`.

| Operation | HTTP route |
| --- | --- |
| Create audit log | `POST /api/v1/audit-logs` |
| Get all audit logs | `GET /api/v1/audit-logs` |
| Verify integrity | `GET /api/v1/audit-logs/integrity` |

For assignment verification, the equivalent endpoint is also available at `GET /audit/verify`. The response includes `valid`, `checkedRecords`, `firstInconsistentRecordId`, and `violationType`. A broken link reports `PREVIOUS_HASH_MISMATCH`; modified event metadata reports `HASH_MISMATCH`; modified payload data reports `PAYLOAD_COMMITMENT_MISMATCH`.

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

There are no generic event replacement or deletion endpoints. The dedicated redaction and retention endpoints may only change privacy/lifecycle fields; the original event identity, metadata, commitments, and chain hashes remain immutable.

Run locally with `./mvnw spring-boot:run` (or `mvnw.cmd spring-boot:run` on Windows).

## Database profiles

The default `local` profile uses a persistent H2 database at `./data/audit-log`. Records therefore remain available through the APIs after a local application restart. The H2 console is available at `/h2-console`; its JDBC URL is `jdbc:h2:file:./data/audit-log`, username `sa`, and blank password unless overridden. Set `AUDIT_DB_PATH`, `AUDIT_DB_USERNAME`, or `AUDIT_DB_PASSWORD` to change these values.

Production uses PostgreSQL:

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://database-host:5432/auditlog
DATABASE_USERNAME=auditlog
DATABASE_PASSWORD=replace-with-a-secret
```

Both profiles use the same `schema.sql` and JDBC repository. Tests use an isolated in-memory H2 database. In production, manage the PostgreSQL schema with a migration tool such as Flyway and provide credentials through a secret manager rather than source control.

## Production monitoring

Spring Boot Actuator provides these production endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | Overall application and database health |
| `GET /actuator/health/liveness` | Liveness probe for container orchestration |
| `GET /actuator/health/readiness` | Readiness probe, including dependency health |
| `GET /actuator/info` | Application identity and version |
| `GET /actuator/metrics` | Available Micrometer metrics (`prod` profile) |
| `GET /actuator/prometheus` | Prometheus scrape output (`prod` profile) |

Health component details are hidden. Local/test profiles expose only health and info. The production profile additionally exposes metrics and Prometheus. At the deployment boundary, allow probe URLs only from the platform and protect metrics/info endpoints with network policy or authentication.

## API documentation

Swagger UI is available at `GET /swagger-ui.html` (or `/swagger-ui/index.html`) and the OpenAPI 3 JSON specification is available at `GET /api-docs`. Every audit, retention, redaction, and export endpoint includes operation, parameter, and response documentation.

## Authentication

All audit, retention, redaction, and export routes require a JWT bearer token. Request one with `POST /api/v1/auth/token`, then send `Authorization: Bearer <accessToken>`. Swagger/OpenAPI documentation and health probes remain public. Local credentials default to `admin` / `change-me-local` and must be changed through `AUTH_USERNAME` and `AUTH_PASSWORD`. Production requires `JWT_SECRET`, `AUTH_USERNAME`, and `AUTH_PASSWORD` environment variables.

## Retention, redaction, and export

`POST /audit/retention/archive?olderThanDays=90` soft-archives older records. Archive metadata is deliberately outside the event hash: records remain in storage and in verification, so legitimate archival does not break the chain. Archived events are omitted from normal queries.

`PATCH /audit/records/{id}/redactions` accepts JSON Pointer paths to scalar fields, for example `{"fields":["/accountNumber","/person/ssn"]}`. At write time the service generates a random 128-bit salt for every scalar leaf and commits to `SHA-256(base64Salt + ":" + canonicalValue)`. It builds a deterministic root over the path/commitment map and hashes that root into the chain. Redaction replaces the visible value with `[REDACTED]` and permanently removes that field's salt while retaining its one-way commitment and the original event hash. Without the salt, low-entropy removed values cannot practically be tested with an offline dictionary. Verification rebuilds the root, verifies every unredacted value using its salt, checks the payload shape, confirms every declared redaction contains the marker, and confirms redacted fields no longer have salts.

Trade-offs and limitations: the flat commitment tree and redaction marker preserve field paths and JSON shape. Salt destruction prevents verification of the original redacted value by design; it only proves that the retained commitment is the one covered by the original event hash. Java cannot guarantee immediate physical memory erasure of prior immutable objects. For stronger operational guarantees, encrypt fields and destroy per-field encryption keys, use durable append-only storage, and authenticate retention/redaction operations. This prototype is not a substitute for encrypted persistent storage.

`GET /audit/export?actorId=user-42` (or `?resourceId=customer-9`) creates an export bundle. Exactly one filter is required and archived matching records are included. Every matching record includes its event hash, predecessor hash, payload-root commitment, field commitments, remaining non-sensitive salts, and redaction metadata.

The bundle also includes a payload-free `chainProof` for every event in the service chain. A recipient can recompute each chain-link hash, verify `previousHash` ordering through `chainHeadHash`, and apply `filterType`/`filterValue` to the proof to confirm that no matching record was omitted. A SHA-256 `bundleDigest` covers the export metadata, records, and complete proof. The service signs that digest with Ed25519 and includes the X.509-encoded public key and its fingerprint (`signingKeyId`). The canonical form is JSON with object keys sorted lexicographically, array order preserved, and scalar JSON representations unchanged.

Verification order:

1. Remove `bundleDigest`, `signatureAlgorithm`, `signingPublicKey`, `signingKeyId`, and `signature`; canonicalize the remaining object and compare its SHA-256 digest with `bundleDigest`.
2. Confirm `signingKeyId` equals the first 16 hexadecimal characters of `SHA-256(signingPublicKey)` and verify the Ed25519 signature over the UTF-8 hexadecimal `bundleDigest`.
3. Recompute every proof event hash from `id`, event metadata, `payloadCommitment`, `timestamp`, and `previousHash`; check the chain starts at `GENESIS` and ends at `chainHeadHash`.
4. Recompute each record's payload commitment tree, event hash, redaction rules, and membership in the proof.
5. Apply the declared filter to `chainProof` and require its matching IDs to equal the exported record IDs.

The signing key is process-local in this prototype. The bundled public key detects accidental or partial bundle alteration, while authenticating the exporter requires the recipient to obtain and pin `signingKeyId` through a trusted channel. A production deployment must persist the private key in a KMS/HSM and publish its trusted public-key fingerprint independently.
