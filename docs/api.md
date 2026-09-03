# API reference

Base URL for a local run: `http://localhost:8080`. Audit APIs require `Authorization: Bearer <accessToken>`.

## Authentication

| Method | URL | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/token` | Exchange configured local/production credentials for a JWT. |

```json
{
  "username": "admin",
  "password": "change-me-local"
}
```

The response contains `accessToken`, `tokenType` (`Bearer`), and `expiresIn`. Local credentials are development defaults only; configure production credentials with environment variables.

## Audit records

| Method | URL | Purpose |
| --- | --- | --- |
| `POST` | `/audit` or `/api/v1/audit-logs` | Append an event. |
| `GET` | `/audit` or `/api/v1/audit-logs` | Query non-archived events. |
| `GET` | `/audit/verify` or `/api/v1/audit-logs/integrity` | Verify the complete chain. |

Create request:

```json
{
  "eventType": "RECORD_UPDATED",
  "actorId": "user-42",
  "resourceType": "customer",
  "resourceId": "customer-9",
  "payload": { "source": "admin-ui", "accountNumber": "1234567890" }
}
```

The server assigns the UTC timestamp. Query parameters are optional and combine using AND: `actorId`, `resourceType`, `resourceId`, `eventType`, `from`, `to`, `page` (zero-based), and `size` (1–100). `from` and `to` are inclusive ISO-8601 UTC timestamps.

## Lifecycle, privacy, and export

| Method | URL | Purpose |
| --- | --- | --- |
| `POST` | `/audit/retention/archive?olderThanDays=90` | Soft-archive qualifying records. |
| `PATCH` | `/audit/records/{id}/redactions` | Redact committed scalar payload fields. |
| `GET` | `/audit/export?actorId=user-42` | Export records for one actor. |
| `GET` | `/audit/export?resourceId=customer-9` | Export records for one resource. |

Redaction request:

```json
{
  "fields": ["/accountNumber", "/person/ssn"]
}
```

`fields` uses RFC 6901 JSON Pointer notation and must name scalar payload values. The export endpoint requires exactly one of `actorId` or `resourceId`.

## Operations and documentation

The following endpoints are public: `GET /swagger-ui.html`, `GET /api-docs`, `GET /actuator/health`, `GET /actuator/health/liveness`, `GET /actuator/health/readiness`, and `GET /actuator/info`.

Production additionally exposes authenticated `GET /actuator/metrics` and `GET /actuator/prometheus`.
