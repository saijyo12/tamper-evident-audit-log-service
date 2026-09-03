# Scenario B design: retention, structured redaction, and bulk export

## Decomposition and execution

1. Keep records in the chain after their normal-query visibility changes, so archival cannot create a false break.
2. Commit to each scalar payload field at insert time before any privacy operation is requested.
3. Replace only selected visible scalar values and destroy the matching salts while retaining their hash-bound commitments.
4. Export matching records with complete-chain proof and a signed canonical digest so recipients can validate integrity and completeness.
5. Test archival, redaction, signature/digest validation, and altered export data.

## Retention policy

`POST /audit/retention/archive?olderThanDays=N` soft-archives qualifying records. The API never physically deletes records. Standard queries omit archived records, while verification and export retain them. Archive state is lifecycle metadata and deliberately not part of the event hash; this prevents legitimate archival from creating a false integrity failure.

## Structured redaction

Replacing an original payload value would normally alter the event hash, while retaining it violates the privacy requirement. The implementation creates a random salt and a commitment for each scalar payload leaf when the event is inserted. A deterministic root over every path and commitment is included in the event hash.

A redaction request, for example `PATCH /audit/records/{id}/redactions` with `{"fields":["/accountNumber","/person/ssn"]}`, replaces the displayed values with `[REDACTED]`, retains their commitments, and destroys their salts. Verification recomputes commitments for unredacted leaves; for redacted leaves it checks the marker, retained commitment, and destroyed salt. The original event hash and chain therefore remain verifiable without retaining the sensitive values.

The trade-off is intentional: after a salt is destroyed, the service cannot prove the former secret value. It can prove only that the retained commitment was included in the original hash. JSON paths and shape remain visible, and Java/runtime/database backups cannot guarantee immediate physical erasure. Stronger production erasure should pair this with per-field envelope encryption and encryption-key destruction.

## Verifiable bulk export

`GET /audit/export` requires exactly one filter: `actorId` or `resourceId`. It returns every matching record, including archived matches, along with a payload-free proof of the complete chain. The proof lets a recipient apply the declared filter independently and detect omitted matching records.

The export bundle includes the chain head, canonical SHA-256 bundle digest, Ed25519 signature, signing public key, and public-key fingerprint. A recipient canonicalizes the unsigned bundle, checks the digest, verifies the signature and key fingerprint, rebuilds the proof-chain hashes from `GENESIS`, verifies exported record commitments, and confirms the filtered proof IDs exactly match the exported IDs.

The included signing key is process-local for this implementation. Production should store and rotate a durable key in a KMS/HSM and distribute/pin the trusted public-key fingerprint through a separate trusted channel.

## Validation

Service and MVC tests confirm that archived records disappear from ordinary query results but remain valid in the chain, that nested and array scalar fields can be redacted without invalidating verification, and that changing a visible unredacted field after redaction is detected. Export tests require exactly one filter, include archived matching records, validate the canonical digest and Ed25519 signature, and demonstrate that removing a record from an exported bundle causes validation to fail.
