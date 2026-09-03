# Swagger and OpenAPI usage

## Purpose

Swagger UI provides an interactive browser for the audit-log APIs. The OpenAPI JSON document describes the same routes for Postman, client generation, or external API review.

## Open Swagger UI

Start the application, then open:

```text
http://localhost:8080/swagger-ui.html
```

The direct UI route also works:

```text
http://localhost:8080/swagger-ui/index.html
```

Open the raw OpenAPI JSON document at:

```text
http://localhost:8080/api-docs
```

## What to check

Swagger should show these groups and endpoints:

| Group | Main endpoints |
| --- | --- |
| Authentication | `POST /api/v1/auth/token` |
| Audit records | `POST /audit`, `GET /audit`, `GET /audit/verify` |
| Retention | `POST /audit/retention/archive` |
| Redaction | `PATCH /audit/records/{id}/redactions` |
| Export | `GET /audit/export` |

Swagger also documents the versioned audit routes under `/api/v1/audit-logs`.

## Test an endpoint from Swagger UI

### 1. Request a JWT

1. Expand `POST /api/v1/auth/token`.
2. Click **Try it out**.
3. Use this request body:

```json
{
  "username": "admin",
  "password": "change-me-local"
}
```

4. Click **Execute**.
5. Copy `accessToken` from the response.

### 2. Call a protected endpoint

Swagger documents the protected endpoints, but use Postman for the simplest bearer-token workflow. In Postman, use `Authorization: Bearer <accessToken>` when testing create, query, verify, retention, redaction, and export.

For a simple public Swagger test, use the token endpoint. For operational checks, open:

```text
http://localhost:8080/actuator/health
http://localhost:8080/actuator/info
```

## API contract details

- `POST /audit` returns `201 Created` for a valid audit event.
- `GET /audit` supports optional actor, resource, event type, time-range, and pagination filters.
- `GET /audit/verify` returns chain integrity status and the first violation when a chain is broken.
- Redaction paths use RFC 6901 JSON Pointer syntax, for example `/accountNumber`.
- Export requires exactly one filter: `actorId` or `resourceId`.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Swagger page does not load | Confirm the application is running on port 8080 and open `/swagger-ui.html`. |
| `/api-docs` is not JSON | Confirm the URL is exactly `http://localhost:8080/api-docs`. |
| Protected request returns `401` | Obtain a fresh token and send one correctly formatted bearer header. |
| Request returns `400` | Inspect Swagger's request schema and required query parameters/body fields. |

Swagger and `/api-docs` are public in the current prototype to simplify API exploration. Restrict or disable them in production if required by organizational security policy.
