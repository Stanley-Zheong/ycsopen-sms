# Phase 23 Test Matrix

| Obligation | Test / command | Evidence |
| --- | --- | --- |
| OBL-F-6-4-A, OBL-F-6-4-B | `mvn -f core/pom.xml -Dtest=HmacRequestAuthenticatorTest,HmacSignatureVerifierTest test` | `EVIDENCE/mvn-test.log` |
| OBL-HTTP-IDEMPOTENCY-001 | `mvn -f core/pom.xml -Dtest=MessageAcceptanceIdempotencyServiceTest,MessageSubmitServiceTest test` | `EVIDENCE/mvn-test.log` |
| OBL-F-6-1-A/B/C, field obligations | `mvn -f core/pom.xml test` | `EVIDENCE/mvn-test.log` |
| Web contract synchronization | `npm --prefix web test` and `npm --prefix web run build` | `EVIDENCE/npm-test.log`, `EVIDENCE/npm-build.log` |
| Obligation ownership | `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner secure-http-message-acceptance --assert-unique --assert-traced` | `EVIDENCE/prd-obligations.log` |
