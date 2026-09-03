# AI usage and engineering traceability

This concise log records the material AI-assisted tasks in this repository. The engineer defined the requests, reviewed the outputs on the local machine, and owns the final design, validation, and submission decisions.

| Area | Brief engineer request | AI-assisted contribution | Decision and rationale | Engineer validation / ownership |
| --- | --- | --- | --- | --- |
| Requirement review | Review the audit-log requirements and identify gaps. | Compared the API, hash chain, retention, redaction, export, security, tests, and documentation with the assignment. | **Accepted as review input.** Findings identified documentation and validation gaps; no source change was made during the review. | The engineer decides which findings to address. |
| Core audit service | Implement and test insert, query, and verification behavior. | Assisted with API contracts, SHA-256 chain calculations, query filtering/pagination, and test cases. | **Accepted.** These capabilities directly satisfy the core audit-log scenario. | The engineer tested the APIs locally and through Postman. |
| Retention and redaction | Add archival and sensitive-payload redaction without breaking integrity. | Assisted with salted scalar commitments, markers, salt removal, verification, and trade-off documentation. | **Accepted.** The commitment approach preserves tamper evidence while removing visible sensitive values. | The engineer selected the approach and verified redaction preserves chain integrity. |
| Export | Add a verifiable bulk export endpoint. | Assisted with filter validation, complete-chain proof metadata, digest/signature data, and verification tests. | **Accepted with documented limitation.** Process-local signing keys are suitable for the prototype; durable KMS/HSM keys are required in production. | The engineer reviewed export contents and limitations. |
| Persistence and operations | Use H2 locally, PostgreSQL in production, and add operational endpoints. | Assisted with YAML profiles, JDBC persistence, schema initialization, Actuator, Swagger, and setup documentation. | **Accepted.** One repository implementation is used across local H2 and production PostgreSQL profiles. | The engineer owns production credentials, migrations, monitoring policy, and deployment controls. |
| Authentication | Add JWT authentication. | Assisted with HS256 token issuance/validation, protected endpoints, and integration testing. | **Accepted with constraint.** JWT proves authenticated access; fine-grained roles and token revocation remain future work. | The engineer tested bearer-token use in Postman and must replace local secrets before production use. |
| Scenario documentation | Correct the scenario mapping and document Scenario C without code. | Assisted with the Scenario A/B reorganization and the documentation-only compliance-reporting design. | **Modified after engineer feedback.** Retention, redaction, and export belong to Scenario B; Scenario C is an ambiguity/design exercise, not a required code feature. | The engineer clarified the intended scope and approved the revised structure. |
| Tests and documentation | Add tests, Postman guidance, and engineering documents. | Assisted with unit/integration tests, payloads, API/architecture/risk documentation, and final-summary material. | **Accepted.** Automated verification passed before the final documentation-only updates. | The engineer reviews documents and is responsible for final verification before submission. |

## Not adopted or intentionally deferred

- **No unsupported compliance-reporting code was added for Scenario C.** The product statement lacks jurisdiction, required fields, recipient workflow, and retention/legal-hold policy. A design and explicit scope boundary were documented instead of inventing compliance behavior.
- **No production key-management, multi-instance chain coordinator, or fine-grained authorization was represented as complete.** These items are documented as limitations rather than claimed as delivered functionality.
- **No AI output is accepted automatically.** Changes are made only after an engineer request and are subject to local review and validation.

## Use boundaries

AI was used as an engineering assistant for analysis, implementation suggestions, debugging, tests, and documentation. It was not treated as an autonomous decision maker. The engineer owns the design choices, reviews generated changes, validates behavior locally, and decides what is included in the final repository.
