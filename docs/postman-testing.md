# Postman testing guide

## Setup

1. Start the service: `mvnw.cmd spring-boot:run`.
2. Import `postman/audit-log-service.postman_collection.json`.
3. Set `baseUrl` to `http://localhost:8080` if the collection does not already use it.
4. Run **Get JWT token** first. Its test script saves `accessToken` for later requests.

## Recommended request order

1. `POST {{baseUrl}}/api/v1/auth/token` — obtain a token.
2. `GET {{baseUrl}}/audit/verify` — verify the empty or current chain.
3. `POST {{baseUrl}}/audit` — create an event using `postman/create-audit-event.json`.
4. `GET {{baseUrl}}/audit?actorId=user-42&page=0&size=10` — query it.
5. `PATCH {{baseUrl}}/audit/records/{{recordId}}/redactions` — redact `/accountNumber` and `/person/ssn`.
6. `GET {{baseUrl}}/audit/verify` — confirm redaction did not break the chain.
7. `GET {{baseUrl}}/audit/export?actorId=user-42` — download the verifiable bundle.
8. `POST {{baseUrl}}/audit/retention/archive?olderThanDays=90` — apply archival policy.
9. `GET {{baseUrl}}/audit/verify` — confirm archival did not break the chain.

## Authentication troubleshooting

Choose Postman's **Bearer Token** authentication type and place only `{{accessToken}}` in the Token input. Do not put `Bearer` in the Token input and do not add a second `Authorization` header; either issue generates a malformed header.

The development defaults are `admin` and `change-me-local`. Tokens have a one-hour lifetime by default. Obtain a fresh token after expiry, after changing `JWT_SECRET`, or when testing a different running application instance.

## Expected results

Create returns `201`; normal query, verification, redaction, retention, and export calls return `200`. Missing/invalid bearer tokens return `401`. Invalid event data, page sizes, retention windows, JSON pointers, or export filters return `400`.
