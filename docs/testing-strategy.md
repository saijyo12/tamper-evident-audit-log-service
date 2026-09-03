# Testing strategy

## Automated tests

Run all checks with:

```powershell
.\mvnw.cmd clean verify
```

The suite contains 33 tests across these layers:

| Layer | Coverage |
| --- | --- |
| Service unit tests | hash creation, predecessor linkage, query filtering, tamper detection, archival, redaction, and export behavior. |
| Repository tests | in-memory repository ordering and data behavior used for isolated service tests. |
| MVC integration tests | create/query/verify endpoints, request validation, and response contracts with H2. |
| JWT integration test | unauthenticated requests receive `401`, valid tokens authorize protected APIs, invalid tokens are rejected. |

## Manual verification in Postman

Use the request order in [postman-testing.md](postman-testing.md). The essential acceptance sequence is create an event, verify success, change a stored record directly in a non-production test database, then verify again and expect a failed result identifying the first inconsistency.

Also test legitimate state changes: redact scalar values, archive older records, and verify after each operation. Both should leave the chain valid. Export an actor/resource bundle and preserve the JSON response as the artifact for independent signature and proof verification.

## Production test considerations

Run integration tests against PostgreSQL in CI, include migration validation, and add load/concurrency testing before horizontally scaling writers. Add security tests for secret rotation, expired tokens, missing headers, endpoint authorization, and monitoring access policy. Use backup/restore exercises to validate the operational retention and signing-key plan.
