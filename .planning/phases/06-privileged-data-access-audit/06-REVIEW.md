---
phase: 06-privileged-data-access-audit
reviewed: 2026-09-07T12:57:26+08:00
depth: deep
files_reviewed: 68
files_reviewed_list:
  - README.md
  - core/docs/API.md
  - core/docs/ROADMAP.md
  - core/src/main/java/com/ycsopen/sms/core/common/exception/GlobalExceptionHandler.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/CorrelationIdFilter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/JwtAccessVerifier.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/JwtAuthenticationFilter.java
  - core/src/main/java/com/ycsopen/sms/core/common/web/TrustedProxyClientIpResolver.java
  - core/src/main/java/com/ycsopen/sms/core/config/RuntimeDatabaseGrantCallback.java
  - core/src/main/java/com/ycsopen/sms/core/config/SecurityConfig.java
  - core/src/main/java/com/ycsopen/sms/core/config/WebMvcConfig.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/AuthService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/IdentitySessionService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/LoginAnomalyService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/PlatformAccountPhoneStore.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/PrivilegedDataService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/RoleAdministrationService.java
  - core/src/main/java/com/ycsopen/sms/core/service/audit/OperationAuditService.java
  - core/src/main/java/com/ycsopen/sms/core/service/audit/SecurityEventService.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/AuthController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/OperationAuditController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/PlatformAccountController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/PlatformRoleController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/PrivilegedDataController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/SecurityEventController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/SessionController.java
  - core/src/main/java/com/ycsopen/sms/core/web/interceptor/OperationAuditInterceptor.java
  - core/src/main/resources/application-dev.yml
  - core/src/main/resources/application.yml
  - core/src/main/resources/db/migration/V1__init_schema.sql
  - core/src/main/resources/db/migration/V1400__console_identity_platform_rbac.sql
  - core/src/main/resources/db/migration/V1500__privileged_audit_storage.sql
  - core/src/main/resources/db/migration/V1501__privileged_data_permissions.sql
  - core/src/main/resources/security/protected-data-inventory.json
  - core/src/test/java/com/ycsopen/sms/core/common/exception/GlobalExceptionHandlerTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/web/TrustedProxyClientIpResolverTest.java
  - core/src/test/java/com/ycsopen/sms/core/config/RuntimeDatabaseGrantCallbackTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/AuthServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/LoginAnomalyServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/PlatformAccountPhoneStoreTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/PrivilegedDataServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/audit/OperationAuditInterceptorTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/audit/OperationAuditSecurityTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/audit/OperationAuditServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/audit/SecurityEventServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase01MySqlIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase05IdentityMySqlIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase06AuditMySqlIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/web/controller/PrivilegedDataControllerTest.java
  - core/src/test/java/com/ycsopen/sms/core/web/controller/SecurityEventControllerTest.java
  - core/tools/init-db.sh
  - docs/使用手册.md
  - scripts/lib/phase-01/service_checks.rb
  - web/docs/ROADMAP.md
  - web/src/api/audit.ts
  - web/src/api/client.ts
  - web/src/api/identity.ts
  - web/src/components/common/ModalDialog.tsx
  - web/src/components/layout/AdminLayout.tsx
  - web/src/pages/admin/identity/UserManagementPage.tsx
  - web/src/pages/admin/identity/useIdentityAccess.ts
  - web/src/pages/admin/security/OperationAuditPage.tsx
  - web/src/pages/admin/security/SecurityEventsPage.tsx
  - web/src/router/routes.tsx
  - web/src/store/authStore.ts
  - web/src/styles/index.css
  - web/test/scripts/privileged-audit.spec.ts
  - web/test/unit/privileged-audit.test.tsx
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
verdict: PASS
impact_counts:
  blocker: 0
  high: 0
  medium: 0
  low: 1
---

# Phase 06: Deep Code Review Report

**Reviewed:** 2026-09-07T12:57:26+08:00
**Depth:** deep
**Status:** PASS
**BLOCKER/HIGH:** 0 BLOCKER, 0 HIGH

## Narrative Findings (AI reviewer)

## Summary

The repaired Phase 06 implementation was re-read across the request interceptor, service and transaction boundaries, current RBAC, Flyway schema/grants and V1500 prerequisites, login detections, API error mapping, React reveal lifecycle, UI contract, and deployment documentation. All eight previously actionable findings and the subsequent V1500 and IPv6-literal hardening concerns are closed. No remaining BLOCKER, HIGH, MEDIUM, or actionable LOW defect was found in the scoped implementation.

All seven owned obligations have matching executable implementation and evidence paths. The authorization split is correct: `privileged:data:reveal` is a BUTTON permission that exposes the UI action, while the server independently requires the API permission `privileged:data:reveal:api` plus DATA scope `identity:accounts:all`; the explicit ADMIN override is consistent on both sides. The BUTTON permission is not incorrectly treated as API authorization.

## Previous Findings Rechecked

| Finding | Result | Re-review evidence |
| --- | --- | --- |
| CR-01 — runtime account could defeat append protection | CLOSED | Flyway and runtime principals are separate. The post-migration callback removes schema-wide runtime grants, grants only `SELECT/INSERT` on audit/event stores, and exposes one constrained finalization procedure. Real MySQL verifies runtime `UPDATE`, `DELETE`, `TRUNCATE`, trigger drop, and table drop are denied. |
| CR-02 — terminal audit failure could erase evidence of an executed operation | CLOSED | `preHandle` commits a structural `STARTED` row before controller execution and fails closed if that insert fails. Completion is the sole STARTED-to-terminal transition. A finalization outage leaves a searchable `STARTED` row, as defined by SPEC/DR-06-007 and proved in real MySQL. |
| CR-03 — bulk-export retries ignored record count | CLOSED | The bounded safe summary contains `count`, exact comparison includes that summary, identical count returns the existing row, and changed count raises a deduplication conflict. |
| WR-01 — proxy address replaced originating client attribution | CLOSED | One resolver is used by login, reveal, and operation auditing. It trusts only a loopback peer with exactly one numeric XFF value. The documented same-host Nginx overwrites XFF with `$remote_addr`; appended/spoofable chains are rejected and tested. |
| WR-02 — invalid filters became HTTP 500 | CLOSED | Controlled event/result values reject with `ResponseStatusException(400)`, the global advice preserves explicit response status, and binding failures such as malformed time/page are also mapped to 400. MockMvc exercises the actual advice path. |
| WR-03 — emitted bulk-export results were absent from UI filters | CLOSED | Producer, controller allowlist, UI selector, and documentation all expose `DETECTED`, `BLOCKED`, `SUCCESS`, and `FAILURE`. |
| WR-04 — Phase 06 controls/actions lacked stable automation selectors | CLOSED | The production UI contract traces 53 documented selectors over three routes. Controls, query/reset/retry actions, conditional query states, pagination, result rows, detail actions, and reveal elements are inventoried and present in implementation/tests. |
| WR-05 — public raw phone-decryption seam bypassed purpose/audit orchestration | CLOSED | The plaintext method is package-private, and the public reveal flow remains the purpose-bound transactional service whose audit insert must succeed before a response is returned. Ordinary public reads remain masked. |
| Claude follow-up — V1500 binlog prerequisite was not operationally fail-fast | CLOSED | The Flyway callback checks the two MySQL global variables immediately before migration 1500. With binlog enabled and trusted creators disabled it fails before V1500 with an actionable DBA message; after the accepted setting is enabled, the same schema resumes from 1402 and completes 1500/1501. The local initialization script performs the same preflight, and deployment docs prohibit granting `SUPER` as a workaround. |
| Claude follow-up — leading-dot IPv6 candidate could reach the JDK literal parser | CLOSED | IPv6 candidates must now begin with `:` or a hexadecimal digit before the strict character whitelist and JDK literal parse. The `.ff:1` regression case is rejected and falls back to the trusted direct peer; the focused resolver suite passes 4/4. |

## Security and Contract Conclusions

- Operation audit content is structural and redacted: request values, bodies, authorization headers, plaintext phone data, and ciphertext do not enter the stored summary.
- An authenticated console attempt is persisted before controller execution. Direct runtime mutation or destruction of the audit store is denied; terminal transition is exactly once through the definer procedure.
- Audit/event searches require their current read permission and default to the authenticated actor unless the corresponding DATA permission or ADMIN override is present.
- Unusual-login, fifth-failure, and bulk-export detections use stable source digests, exact duplicate comparison, and attributable actor/tenant/IP/trace fields without persisting their raw source identifiers.
- The fifth rejected login commits the account lock, all five history rows, and exactly one repeated-failure event despite the expected `BusinessException`; a real-MySQL test exercises the production `noRollbackFor` transaction boundary.
- Reveal requires the exact API and DATA authorities, a controlled non-null purpose, a no-store response, and a linked audit. The browser keeps plaintext only in local component state and clears it on close, expiry, stale async completion, or unmount.
- Backend DTO nullability and frontend rendering agree for nullable event IP/trace and audit trace values; audit detail renders operation and resource separately.
- Forwarded client values accept only strict IPv4 octets or an IPv6 literal. IPv6 candidates must start with `:` or a hexadecimal digit, so leading-dot and other hostname-shaped input cannot reach the JDK parser; invalid IPv4 is likewise rejected without DNS resolution. Rejected input falls back to the trusted direct peer.

## Accepted LOW Observation — Legacy `operation_logs`

The unused V1 `operation_logs` table remains alongside the Phase 06-owned `privileged_operation_audits` table. Under DR-06-005 this is an acceptable, non-blocking LOW observation rather than a defect: there is one active writer and one active query model, no dual write or misleading compatibility claim, and changing the legacy table would expand into Phase 3 protected-inventory/digest ownership without improving the delivered boundary. A later owner may retire the legacy table if it has an actual migration need; Phase 06 should not add backfill or synchronization machinery pre-emptively.

## Over-engineering Review

The final design is proportionate to the stated security guarantees. The existing runtime-grant callback now owns one migration-specific preflight in addition to post-migration grants, avoiding a separate bootstrap framework. One finalization procedure is necessary to make the database protection claim true. The phase adds no broker, SIEM adapter, export subsystem, alert orchestration, archive pipeline, mobile UI, or alternate-browser matrix. No additional gate or abstraction is recommended.

## Verification Executed or Independently Reconfirmed

- `mvn -f core/pom.xml test` — PASS in the final main verification: 483 tests, 0 failures, 0 errors, 20 conditional skips.
- `mvn -f core/pom.xml -Dtest=TrustedProxyClientIpResolverTest,OperationAuditInterceptorTest,OperationAuditServiceTest,OperationAuditSecurityTest,SecurityEventServiceTest,SecurityEventControllerTest,PrivilegedDataServiceTest,PrivilegedDataControllerTest,AuthServiceTest,LoginAnomalyServiceTest test` — PASS: 26/26.
- `mvn -f core/pom.xml -Dtest=TrustedProxyClientIpResolverTest,SecurityEventControllerTest,GlobalExceptionHandlerTest,OperationAuditSecurityTest test` — PASS after the final proxy/error-boundary repairs: 12/12.
- Phase 05 plus Phase 06 real-MySQL verification — PASS in the latest main verification: 3/3, including the fifth-rejected-login commit boundary.
- `mvn -f core/pom.xml -Dtest=RuntimeDatabaseGrantCallbackTest,TrustedProxyClientIpResolverTest test && bash -n core/tools/init-db.sh` — PASS independently after the V1500/IP hardening: 6/6 plus shell syntax.
- `mvn -f core/pom.xml -Dtest=TrustedProxyClientIpResolverTest test` — PASS independently after the final IPv6 first-character guard: 4/4, including rejection of `.ff:1`.
- `mvn -f core/pom.xml -Dphase01.integration.enabled=true -Dtest=Phase06AuditMySqlIntegrationTest test` — PASS independently after hardening: OFF fails before V1500 at schema version 1402, ON resumes through 1501, then runtime grant/integrity checks pass.
- `npm --prefix web test -- --run` — PASS: 32/32.
- Frontend lint and production build — PASS in the latest main verification.
- Installed Google Chrome Phase 06 Playwright — PASS in the latest main verification: 7/7.
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 06 --package privileged-data-access-audit --stage production` — PASS: 53 selectors, three routes.
- `git diff --check f613c1c` with the explicitly protected user-owned Phase 02 image excluded — PASS.

## Verdict

**PASS.** There are **0 BLOCKER and 0 HIGH** findings. The prior actionable findings are resolved, and the retained legacy-table observation is an explicitly bounded LOW decision with no active behavioral ambiguity. Phase 06 is suitable for TODO/evidence closure and commit after the orchestrator records its final verification artifact.

---

_Reviewed: 2026-09-07T12:57:26+08:00_
_Reviewer: independent Phase 06 reviewer (gsd-code-reviewer)_
_Depth: deep_
