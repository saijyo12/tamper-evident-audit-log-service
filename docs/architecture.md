# Architecture

## Components

```text
HTTP controllers -> AuditLogService -> JdbcAuditLogRepository -> H2 (local) / PostgreSQL (prod)
                         |                 
                         +-> hash-chain, payload commitments, retention, export signing

JWT token endpoint -> JwtService -> HS256 token validation filter -> protected controllers
```

`AuditLogController` exposes append, query, and verification. Dedicated controllers isolate retention, redaction, export, and authentication concerns. `HashingAuditLogService` owns all integrity rules; the JDBC repository persists records without deciding their cryptographic meaning.

## Storage profiles

The default `local` profile uses a persistent file-backed H2 database at `./data/audit-log`. The `prod` profile uses PostgreSQL and expects its connection information through environment variables. Both initialize the same schema and use the same JDBC repository, avoiding profile-specific business behavior.

## Chain invariant

Each event stores:

- a stable record ID and server-assigned timestamp;
- event metadata and a payload commitment root;
- `previousHash`, set to `GENESIS` for the first record;
- `hash`, SHA-256 of canonical event content plus `previousHash`.

Appending is serialized by the service so that the new record links to the current chain head. Verification reads every record in order, recalculates its payload commitment and hash, and checks predecessor continuity. It reports the first inconsistent record and violation type.

## Privacy model

For each scalar payload leaf, the service generates a random salt and stores a commitment to the path and canonical scalar value. The deterministic root of all field commitments is part of the event hash. Redaction replaces the visible scalar with `[REDACTED]`, destroys its salt, and retains the commitment. Therefore the original event hash remains meaningful without retaining the source value.

## Export model

An export contains matching records and a payload-free proof of the full chain, enabling a recipient to prove no matching record was omitted. A canonical bundle digest is signed using Ed25519. The bundle includes the signing public key and key fingerprint so a recipient can independently validate the bundle after receiving a trusted key fingerprint.
