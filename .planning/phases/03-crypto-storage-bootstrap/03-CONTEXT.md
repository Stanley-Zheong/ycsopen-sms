# Phase 3: Cryptographic storage and migration bootstrap - Context

**Gathered:** 2026-08-31
**Status:** Ready for planning
**Mode:** Auto-generated autonomous infrastructure phase

<domain>
## Phase Boundary

Deliver the cryptographic persistence foundation for protected database fields, protected evidence objects, envelope-key lifecycle, log redaction, and an idempotent/resumable/auditable plaintext migration with integrity verification and failure-safe rollback. Password hashing, identity workflows, role decisions, privileged reveal UI, business-owned tables beyond the registered metadata namespace, and archive lifecycle remain outside this phase.

</domain>

<decisions>
## Implementation Decisions

### Locked project decisions
- The verified scoped TODO set is the only completion signal; schedules, effort estimates, velocity and percentage status are forbidden.
- The four `crypto-storage-bootstrap` atomic obligations are the authoritative completion units.
- The phase owns only `ycs.sms.crypto-storage-bootstrap.*` schema objects and Flyway versions `V1200-V1299` under expand-migrate-contract rules.
- Master keys must never be stored in application data, source, logs or evidence. Production key access crosses a KMS/HSM adapter boundary; executable tests use a deterministic in-memory adapter without weakening production configuration validation.
- Existing protected plaintext must migrate through a resumable, idempotent, integrity-checked and auditable path with an explicit failure-safe rollback contract.
- Evidence objects must be encrypted at rest and exposed only through time-limited authorized access, never public direct links.
- Password hashing and credential schema remain Phase 5 responsibilities; masked/reveal authorization and privileged audit remain Phase 6 responsibilities.

### the agent's Discretion
- Internal Java package decomposition, ciphertext envelope format, adapter interfaces, migration batch/checkpoint structure, test fixture layout, and verification tooling may follow repository conventions as long as all locked decisions and atomic obligations remain executable and fail closed.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `core/src/main/java/com/ycsopen/sms/core/common/security/FieldEncryptor.java` already demonstrates AES-256-GCM with fresh 12-byte IVs and authenticated decryption, but directly owns one configuration key and explicitly disclaims production key management.
- `core/src/test/java/com/ycsopen/sms/core/common/security/FieldEncryptorTest.java` provides round-trip, fresh-IV, tamper, wrong-key and malformed-payload tests that can be retained as lower-level crypto vectors.
- Spring Boot 3.3, Java 21, JPA, Flyway, MySQL 8 and H2 test dependencies are already configured in `core/pom.xml`.
- `.planning/tools/validate-phase-entry.rb`, `validate-prd-obligations.rb` and schema ownership validators provide the fail-closed planning gate.

### Established Patterns
- Backend packages live under `com.ycsopen.sms.core`; tests mirror production packages and use JUnit 5/AssertJ.
- Database changes are Flyway-owned and Hibernate uses `ddl-auto: validate` outside isolated tests.
- Configuration is namespaced under `ycsopen.*`, with environment substitution for deployment secrets.
- Human-readable comments explain PRD/security policy; automated evidence is stored under the owning phase `EVIDENCE/` directory.

### Integration Points
- Replace the direct single-key boundary in `FieldEncryptor` with versioned envelope encryption backed by a KMS/HSM port and explicit key identifiers.
- Add protected persistence converters/adapters without taking ownership of later business schemas.
- Add protected object storage and signed-access ports that can be exercised by an authoritative local adapter.
- Claim only Phase 3 metadata/checkpoint/audit schema objects in `SCHEMA-CLAIMS.md` using `V1200-V1299` migrations.
- Add leak-scan and migration commands to the Maven test/verification surface and bind every result to the four obligation evidence targets.

</code_context>

<specifics>
## Specific Ideas

- Preserve the useful AES-GCM vector behavior while making ciphertext self-describing by version/key identifier.
- Treat malformed envelopes, unavailable keys, rotation interruption, partial migration, retry and rollback as first-class executable cases.
- Ensure logs and generated evidence never contain sample plaintext, raw data keys, master-key material or public object URLs.

</specifics>

<deferred>
## Deferred Ideas

- Password hashing, authentication and console RBAC are Phase 5.
- Default masking, privileged full reveal, current-RBAC checks and reveal audit are Phase 6.
- Business-module schema adoption of the protected-field boundary occurs in each owning phase.
- Encrypted cold archives, retention and restore are Phase 47.

</deferred>
