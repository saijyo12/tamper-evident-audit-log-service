# JWT authentication usage

## Purpose

JWT bearer authentication protects audit write/query/verification, retention, redaction, export, and protected Actuator endpoints. A caller first obtains a short-lived token, then sends it in an `Authorization` header.

## Local setup

Start the application:

```powershell
.\mvnw.cmd spring-boot:run
```

The default local credentials are:

```text
Username: admin
Password: change-me-local
```

These are development defaults only. Override them with `AUTH_USERNAME` and `AUTH_PASSWORD`. The local JWT signing secret can be overridden with `JWT_SECRET`.

## Get an access token

`POST http://localhost:8080/api/v1/auth/token`

Request body:

```json
{
  "username": "admin",
  "password": "change-me-local"
}
```

Successful response:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

The token is signed using HS256 and expires after the configured lifetime (one hour by default).

## Use the token in Postman

In the request **Authorization** tab:

1. Set **Type** to `Bearer Token`.
2. Paste only the value of `accessToken` into the Token field.
3. Send the request.

Postman generates this header:

```http
Authorization: Bearer <accessToken>
```

Do not paste `Bearer <accessToken>` into the Token field. That would send `Bearer Bearer ...` and returns `401 Unauthorized`.

## Protected and public endpoints

| Endpoint group | JWT required? |
| --- | --- |
| `/audit/**` and `/api/v1/audit-logs/**` | Yes |
| `/audit/export`, `/audit/retention/**`, `/audit/records/**` | Yes |
| `/actuator/metrics`, `/actuator/prometheus` in production | Yes |
| `/api/v1/auth/token` | No |
| `/swagger-ui.html`, `/swagger-ui/**`, `/api-docs/**` | No |
| `/actuator/health/**`, `/actuator/info` | No |
| `/h2-console/**` in local profile | No |

## Quick validation sequence

1. Call `GET http://localhost:8080/audit` with no token. Expected: `401`.
2. Request a token using the credentials above. Expected: `200`.
3. Call `GET http://localhost:8080/audit/verify` with `Authorization: Bearer <accessToken>`. Expected: `200`.
4. Change the token to `invalid-token`. Expected: `401`.

## Production guidance and limitations

- Set `JWT_SECRET`, `AUTH_USERNAME`, and `AUTH_PASSWORD` through a secret manager or protected deployment environment.
- The configured JWT secret must be at least 32 bytes.
- Tokens are short-lived but this prototype does not implement revocation, refresh tokens, issuer/audience validation, or key rotation.
- Authentication is implemented; fine-grained authorization for export, redaction, retention, and compliance operations remains future work.
