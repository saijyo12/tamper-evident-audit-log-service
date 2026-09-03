# Scenario A design: insert, query, and verify

## Decomposition and execution

1. Define the immutable event contract and choose server-assigned UTC timestamps.
2. Design canonical hash material, the `GENESIS` value, and predecessor linkage.
3. Implement append-only write, filtered/paginated query, and complete-chain verification endpoints.
4. Persist chain order and event/hash metadata through the JDBC repository.
5. Validate normal API behavior and prove tamper detection by changing a persisted H2 row directly, then calling the verification API.

## Event insertion

`POST /audit` accepts an event type, actor ID, resource type, resource ID, and structured payload. The service assigns the timestamp in UTC at acceptance time; callers cannot backdate the audit record through the API.

The service creates a cryptographic payload commitment, reads the current chain head, sets `previousHash` to that head (or `GENESIS` for the first record), and calculates the record's SHA-256 hash over canonical event data plus the predecessor hash. The record is then persisted as a new row. No API supports updating or deleting a record.

## Event query

`GET /audit` returns a zero-based page of active records. Every filter is optional and can be combined with the others:

- `actorId`
- `resourceType` and `resourceId`
- `eventType`
- inclusive UTC `from` and `to` timestamps
- `page` and `size`

The response contains the requested content and pagination metadata. Server-side filtering and pagination prevent callers from needing to retrieve the entire audit history for ordinary searches.

## Chain verification

`GET /audit/verify` walks the complete stored sequence, including archived records. For every record it checks the predecessor hash, recalculates the payload commitment, recalculates the record hash, and stops at the first inconsistency.

An intact response reports `valid: true`. A failed response identifies the first inconsistent record and violation type, such as `PREVIOUS_HASH_MISMATCH`, `HASH_MISMATCH`, or `PAYLOAD_COMMITMENT_MISMATCH`. This makes direct database modification detectable through the API.

## Validation

Automated MVC integration tests create events through `POST /audit`, query them, verify the intact chain, and then update `payload_json` directly in the H2 `audit_log_entries` table. `GET /audit/verify` must return `valid: false`, the modified record ID, and `PAYLOAD_COMMITMENT_MISMATCH`. Unit tests additionally cover genesis linkage, multiple-record predecessor linkage, invalid time ranges, and no public update/delete route.
