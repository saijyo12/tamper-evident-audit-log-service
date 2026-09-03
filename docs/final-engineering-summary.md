# Final engineering summary

This service implements an append-only, tamper-evident audit log suitable for the assignment's write, query, verification, retention, redaction, and export scenarios.

## Delivered capabilities

- Append-only event creation with server-assigned UTC timestamps.
- Filtered and paginated query API.
- SHA-256 hash chain with full-chain verification and first-violation reporting.
- File-backed H2 for local development and PostgreSQL configuration for production.
- Soft archival without breaking verification.
- Structured scalar-field redaction that preserves a cryptographic payload commitment.
- Signed, self-contained export bundles with chain proof.
- HS256 JWT bearer authentication for application and protected Actuator routes.
- OpenAPI/Swagger documentation and production-ready health/readiness/liveness endpoints.

## Validation

The Maven verification suite passes with 33 tests, including unit tests for hash-chain/redaction/export behavior, repository tests, controller integration tests, the direct-H2 tampering verification test, and JWT authentication integration tests. See [testing-strategy.md](testing-strategy.md) for the testing scope.

## Operational handoff

Run locally with `mvnw.cmd spring-boot:run`, use `POST /api/v1/auth/token` to obtain a bearer token, and import the supplied Postman collection for the full request sequence. Before production deployment, provide all environment-based secrets, use managed PostgreSQL, persist signing keys in a KMS/HSM, and apply network controls around monitoring endpoints.
