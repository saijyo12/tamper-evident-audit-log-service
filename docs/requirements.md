# Requirements traceability

| Requirement | Implementation |
| --- | --- |
| Write event with type, actor, resource, payload, timestamp | `POST /audit`; timestamp is assigned by the server in UTC. |
| Append-only records | No update or delete API exists. Dedicated retention/redaction operations never change event metadata or event hash. |
| Filtered, paginated query | `GET /audit` supports actor, resource, type, time range, page, and size filters. |
| Tamper-evident sequence | Every record has its own SHA-256 hash and predecessor hash. |
| Detect tampering | `GET /audit/verify` recalculates every link and reports the first violation. |
| Configurable retention | `POST /audit/retention/archive?olderThanDays=N` soft-archives eligible records. |
| Privacy redaction without chain break | Salted scalar commitments and a hash-bound payload commitment root. |
| Verifiable bulk export | `/audit/export` provides selected records, full-chain proof, digest, signature, and public key metadata. |
| Local and production persistence | H2 `local` profile and PostgreSQL `prod` profile. |
| API security | JWT bearer authentication for protected routes. |
| Operational visibility | Actuator health, liveness, readiness, info, metrics, and Prometheus configuration. |

The server rather than the caller assigns timestamps. This makes the audit chronology trustworthy from the service's perspective and prevents a caller from deliberately backdating an event. It does not prove the real-world event occurred at that instant; source-system time can be retained in the payload if needed.
