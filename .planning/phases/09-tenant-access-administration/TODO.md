# Authoritative Phase TODO

All Phase09 scoped TODOs are closed by executable evidence.

- [x] Phase08 dependency delivery: pushed SHA `0468fe5c9fd229c43fc748b8760a8c273527f8bd`; see `08-VERIFICATION.md`.
- [x] Entry/spec/design/schema gates: `ENTRY-REVIEW.md`, `09-SPEC.md`, `09-UI-SPEC.md`, `UI-ELEMENTS.md`, `SCHEMA-CLAIMS.md`; validators PASS.
- [x] Tenant subaccounts and tenant-role isolation: `EVIDENCE/OBL-F-1-3-A.json`, `OBL-F-1-3-B.json`, `OBL-F-2-7-A.json`.
- [x] HTTP API-key policy, one-time secret, protection, masking, and revocation: `EVIDENCE/OBL-F-2-6-A.json`, `OBL-F-2-6-B.json`.
- [x] CMPP metadata, protected password, masking, and revocation: `EVIDENCE/OBL-F-2-6-C.json`.
- [x] Credential persistence without recoverable plaintext: `EVIDENCE/OBL-DATA-10-6-ACCESS.json` and `Phase09TenantCredentialMySqlTest`.
- [x] Backend full suite: `mvn -q -f core/pom.xml test`.
- [x] Frontend suite/lint/build: `npm --prefix web test`, `npm --prefix web run lint`, `npm --prefix web run build`.
- [x] Installed local Chrome 152 at 1440x900, real Spring/Vite/MySQL/MinIO/SoftHSM, six direct blocks: `EVIDENCE/playwright-execution.json`.
- [x] Production UI contract and PRD obligation validators PASS.
- [x] Independent review PASS: `09-REVIEW.md`.
- [x] Claude review PASS: `CLAUDE-REVIEW.md`.
- [x] One atomic commit pushed for Phase09 on `origin/phase/09-tenant-access-administration`.

The scoped TODO set is empty.
