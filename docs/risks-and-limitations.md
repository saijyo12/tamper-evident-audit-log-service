# Risks and limitations

## Cryptographic and privacy limits

- Redaction proves that the retained commitment was part of the original record; after salt destruction, it intentionally cannot prove the original redacted value. This is necessary to meet the privacy objective.
- Visible JSON paths and structure remain after redaction. Field names may themselves disclose context.
- Java cannot guarantee immediate physical erasure of bytes that were previously in heap memory, database logs, snapshots, or backups.
- The service uses SHA-256 commitments and HS256 JWT signing. Production secret rotation and key identifiers should be added for long-lived deployments.

## Export limits

- The Ed25519 signing key is process-local. Restarting the service changes its public key, so external recipients must obtain and pin a trusted key fingerprint through a separate trusted channel.
- A production deployment should store a durable private signing key in a KMS/HSM, rotate it, record key IDs, and preserve historical public keys.

## Data and concurrency limits

- Chain construction is coordinated in the service process. Multiple independently writing service instances need database-level serialization or a dedicated sequencer to maintain one global chain.
- The included schema initialization is practical for development. Use Flyway or Liquibase for controlled PostgreSQL migrations.
- Soft archival retains data by design. It is not a replacement for a legally approved purge/backup-expiry policy.

## Security and operations

- Local credentials and default JWT secret are development conveniences only. They must never be used in production.
- JWT authentication confirms access to this service but does not provide fine-grained authorization, token revocation, refresh tokens, rate limiting, or an identity-provider integration.
- Audit log integrity does not substitute for database access controls, immutable backups, least privilege, monitoring, or incident response procedures.
