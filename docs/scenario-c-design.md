# Scenario C design: compliance reporting for client-account data access

## Ambiguous product request

> Regulators need to be able to audit access to client account data.

This is not yet an implementable requirement. It does not define what counts as access, which regulator/report format applies, who can request a report, which client records are in scope, or what evidence is sufficient.

## Clarification questions and why they matter

| Question | Why it must be answered |
| --- | --- |
| Which actions count as access: view, search, download, print, API retrieval, failed access, or administrative access? | Defines the event taxonomy and prevents material access paths from being omitted. |
| What identifies the client account: account number, internal client ID, household ID, or a combination? | Determines the query key and the privacy model for report output. |
| Which actors must be reported: employees, service accounts, vendors, and automated jobs? | Ensures complete accountability and determines actor identity requirements. |
| Which regulator, jurisdiction, and report format apply? | Drives retention duration, fields, redaction rules, reporting cadence, and export format. |
| What date range, frequency, and delivery mechanism are required? | Determines pagination/report size, scheduling, and whether an API export is sufficient. |
| Who is authorized to generate and receive reports? | Requires role-based authorization, approval workflow, and access logging for the reporting action itself. |
| Must the report show success/failure, purpose of access, source IP/device, location, and correlation ID? | Defines the minimum payload schema and helps investigators understand context. |
| What is the required retention and legal-hold behavior? | Determines when archival is allowed and when deletion/key destruction must be suspended. |
| What evidence must prove report completeness and integrity? | Determines whether the signed export and full-chain proof meet the regulator's standard. |

## Working assumptions for this prototype

Until product/compliance answers the questions above, this prototype assumes:

1. A client account is represented by `resourceType=account` and a stable internal `resourceId`; raw account numbers belong only in redacted payload fields.
2. Every access-producing system submits events such as `ACCOUNT_VIEWED`, `ACCOUNT_SEARCHED`, `ACCOUNT_DOWNLOADED`, or `ACCOUNT_ACCESS_DENIED`.
3. `actorId` identifies the authenticated human user or service principal.
4. The event payload contains the purpose of access, outcome, source system, correlation ID, and other non-secret context required for investigation.
5. A compliance report is an export for one account/resource ID over a requested time range, with a signed integrity proof.
6. Reporting is limited to authorized compliance users; this prototype authenticates callers but does not yet implement a separate compliance-reporting role.

## Proposed technical design

### Event contract

Use the existing append-only audit API with a normalized account-access payload:

```json
{
  "eventType": "ACCOUNT_VIEWED",
  "actorId": "employee-123",
  "resourceType": "account",
  "resourceId": "internal-account-456",
  "payload": {
    "outcome": "SUCCESS",
    "purposeOfAccess": "CLIENT_SERVICE",
    "sourceSystem": "advisor-portal",
    "correlationId": "request-789",
    "accountNumber": "1234567890"
  }
}
```

`accountNumber` is a candidate for structured redaction. The internal `resourceId` remains the stable report key so the report can be generated without exposing the account number.

### Report endpoint evolution

The current endpoint, `GET /audit/export?resourceId={id}`, is the integrity-preserving foundation. A production compliance-report endpoint should add:

- mandatory `from` and `to` bounds;
- an allowed account-access event-type set;
- compliance-specific authorization, approval, and audit logging;
- report metadata: requester, generation time, policy/version, time zone, and case/reference ID;
- a regulator-approved CSV/PDF format in addition to the signed JSON bundle when required;
- an explicit statement of completeness, including the chain range and archival/legal-hold status.

### Controls

- Enforce role-based access so ordinary audit readers cannot export client-access reports.
- Record report creation as an audit event, including requester, purpose, filter, and recipient/case reference.
- Apply least privilege and avoid placing raw client identifiers in URLs, logs, or unredacted exports.
- Use a durable signing key in a KMS/HSM and publish trusted public-key fingerprints separately.
- Add legal-hold state so retention/archive/key-destruction operations cannot compromise an active investigation.

## Implemented versus scoped out

### Implemented

- Append-only account-access-capable events.
- Query by `resourceType`, `resourceId`, actor, event type, and time range.
- Full hash-chain integrity verification.
- Privacy-preserving payload-field redaction.
- Export by resource ID with full-chain proof, digest, and signature metadata.

### Intentionally scoped out

- Regulator-specific report schema, retention period, and delivery channel; these require compliance/legal direction.
- Fine-grained compliance roles, segregation of duties, approval workflow, and recipient authorization.
- Legal holds, case management, report scheduling, and notification workflows.
- Cross-system event ingestion guarantees, identity federation, and device/IP enrichment.
- Durable KMS/HSM key storage and enterprise key rotation.

These are scoped out because the original statement supplies no regulatory jurisdiction, policy, data classification, or operational workflow. Implementing them without that direction would create a potentially non-compliant solution.

## Validation plan

1. Create success and failure account-access events from each in-scope access channel.
2. Query an account by `resourceType=account`, `resourceId`, and a bounded time range.
3. Confirm a direct database modification causes `GET /audit/verify` to report the first inconsistency.
4. Redact a sensitive account-number payload field and confirm verification remains valid.
5. Export the account's records and independently validate the digest, signature, chain proof, and filter completeness.
6. Before production, obtain compliance sign-off on event taxonomy, report contents, access controls, retention/legal-hold policy, and recipient verification procedure.
