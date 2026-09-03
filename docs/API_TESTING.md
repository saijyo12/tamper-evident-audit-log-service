# API URLs and sample payloads

Start the application locally:

```powershell
.\mvnw.cmd spring-boot:run
```

The local base URL is `http://localhost:8080`.

## Get a JWT access token

`POST http://localhost:8080/api/v1/auth/token`

```json
{
  "username": "admin",
  "password": "change-me-local"
}
```

Copy `accessToken` from the response. For all audit, retention, redaction, and export requests, send this header:

```text
Authorization: Bearer {accessToken}
```

Local credentials can be overridden with `AUTH_USERNAME` and `AUTH_PASSWORD`. Production credentials and the `JWT_SECRET` must be supplied through environment variables.

## Create an audit event

`POST http://localhost:8080/audit`

```json
{
  "eventType": "RECORD_UPDATED",
  "actorId": "user-42",
  "resourceType": "customer",
  "resourceId": "customer-9",
  "payload": {
    "changedFields": ["email", "phone"],
    "source": "admin-ui",
    "accountNumber": "1234567890",
    "person": {
      "name": "Jane Doe",
      "ssn": "111-22-3333"
    }
  }
}
```

The server assigns the UTC timestamp and returns the new record `id`, `hash`, and `previousHash`. The equivalent versioned URL is `POST http://localhost:8080/api/v1/audit-logs`.

## Query events

```text
GET http://localhost:8080/audit?actorId=user-42&resourceType=customer&resourceId=customer-9&eventType=RECORD_UPDATED&page=0&size=25
```

Optional inclusive UTC time filters:

```text
GET http://localhost:8080/audit?from=2026-09-01T00:00:00Z&to=2026-09-03T23:59:59Z&page=0&size=50
```

The equivalent versioned query URL is `GET http://localhost:8080/api/v1/audit-logs`.

## Verify the complete hash chain

```text
GET http://localhost:8080/audit/verify
```

The equivalent versioned URL is `GET http://localhost:8080/api/v1/audit-logs/integrity`.

An intact chain returns:

```json
{
  "valid": true,
  "checkedRecords": 1,
  "firstInconsistentRecordId": null,
  "violationType": null,
  "message": "All audit events are intact"
}
```

## Redact sensitive fields

Replace `{recordId}` with the `id` returned by create.

`PATCH http://localhost:8080/audit/records/{recordId}/redactions`

```json
{
  "fields": [
    "/accountNumber",
    "/person/ssn"
  ]
}
```

The response replaces those values with `[REDACTED]`. Run the verification URL afterward; it remains valid.

## Archive records by retention policy

```text
POST http://localhost:8080/audit/retention/archive?olderThanDays=90
```

Archived records are omitted from standard queries but remain included in chain verification and export.

## Export a signed, verifiable bundle

Specify exactly one filter.

```text
GET http://localhost:8080/audit/export?actorId=user-42
GET http://localhost:8080/audit/export?resourceId=customer-9
```

## API documentation and operations checks

```text
GET http://localhost:8080/swagger-ui.html
GET http://localhost:8080/api-docs
GET http://localhost:8080/actuator/health
GET http://localhost:8080/actuator/health/liveness
GET http://localhost:8080/actuator/health/readiness
GET http://localhost:8080/actuator/info
```

When running with `SPRING_PROFILES_ACTIVE=prod`, the following monitoring URLs are also exposed:

```text
GET http://localhost:8080/actuator/metrics
GET http://localhost:8080/actuator/prometheus
```

## Postman

Import [audit-log-service.postman_collection.json](../postman/audit-log-service.postman_collection.json) into Postman. Run **Get JWT token** first; it saves `accessToken` automatically. Then run **Create audit event**, which saves the response ID in `recordId` for **Redact sensitive fields**.
