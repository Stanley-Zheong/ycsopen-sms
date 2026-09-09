---
phase: "05-console-identity-platform-rbac"
reviewed: "2026-09-07T02:27:47Z"
depth: deep
status: clean
files_reviewed: 88
files_reviewed_list:
  - core/src/main/java/com/ycsopen/sms/core/common/exception/GlobalExceptionHandler.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/CorrelationIdFilter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/JwtAccessVerifier.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/JwtAuthenticationFilter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/JwtTokenProvider.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationRunner.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldContexts.java
  - core/src/main/java/com/ycsopen/sms/core/config/SecurityConfig.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/User.java
  - core/src/main/java/com/ycsopen/sms/core/repository/UserRepository.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/AccountStateService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/AuthService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/IdentitySessionService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/LoginAnomalyService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/LoginHistoryService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/PlatformAccountPhoneStore.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/PlatformAccountPolicy.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/PlatformAccountService.java
  - core/src/main/java/com/ycsopen/sms/core/service/account/RoleAdministrationService.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/AuthController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/ChannelController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/DashboardController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/LoginHistoryController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/PlatformAccountController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/PlatformRoleController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/SessionController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ApiResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/LoginHistoryItemResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/LoginHistoryPageResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/LoginRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/PlatformAccountCreateRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/PlatformAccountResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/PlatformAccountUpdateRequest.java
  - core/src/main/resources/db/migration/V1400__console_identity_platform_rbac.sql
  - core/src/main/resources/db/migration/V1401__platform_identity_permissions.sql
  - core/src/main/resources/db/migration/V1402__identity_api_permissions_and_role_audit.sql
  - core/src/test/java/com/ycsopen/sms/core/common/exception/GlobalExceptionHandlerTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/JwtAccessVerifierTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/JwtSecurityBoundaryTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldContextsTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/AccountStateServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/AuthServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/AuthServiceTransactionTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/IdentitySessionServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/LoginAnomalyServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/LoginHistoryServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/PlatformAccountPhoneStoreTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/PlatformAccountPolicyTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/PlatformAccountServiceTransactionTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/PlatformAccountServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/RoleAdministrationServiceJdbcTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/RoleAdministrationServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/account/RoleAdministrationServiceTransactionTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase05IdentityMySqlIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/web/controller/LoginHistoryControllerTest.java
  - core/src/test/java/com/ycsopen/sms/core/web/controller/PlatformAccountControllerAuthorizationTest.java
  - core/src/test/java/com/ycsopen/sms/core/web/controller/PlatformRoleControllerAuthorizationTest.java
  - core/src/test/java/com/ycsopen/sms/core/web/dto/LoginRequestValidationTest.java
  - web/playwright.config.ts
  - web/src/api/auth.ts
  - web/src/api/client.ts
  - web/src/api/identity.ts
  - web/src/api/queryClient.ts
  - web/src/app/App.tsx
  - web/src/components/common/InternalErrorNotice.tsx
  - web/src/components/common/ModalDialog.tsx
  - web/src/components/layout/AdminLayout.tsx
  - web/src/components/layout/TenantLayout.tsx
  - web/src/main.tsx
  - web/src/pages/LoginPage.tsx
  - web/src/pages/admin/identity/AccountOverviewPage.tsx
  - web/src/pages/admin/identity/LoginHistoryPage.tsx
  - web/src/pages/admin/identity/RoleManagementPage.tsx
  - web/src/pages/admin/identity/UserManagementPage.tsx
  - web/src/pages/admin/identity/useIdentityAccess.ts
  - web/src/router/ProtectedRoute.tsx
  - web/src/router/routes.tsx
  - web/src/store/authStore.ts
  - web/src/styles/index.css
  - web/test/scripts/dashboard.spec.ts
  - web/test/scripts/helpers.ts
  - web/test/scripts/identity.spec.ts
  - web/test/unit/auth-session.test.tsx
  - web/test/unit/identity-pages.test.tsx
  - web/test/unit/internal-error-notice.test.tsx
  - web/test/unit/login-page.test.tsx
  - web/vite.config.ts
finding_counts:
  blocker: 0
  high: 0
  medium: 0
  low: 0
  total: 0
---

# Phase 05: Code Review Report

**Reviewed:** 2026-09-07T02:27:47Z
**Depth:** deep
**Files Reviewed:** 88 changed Phase 05 source/config/migration files, plus unchanged called code where cited
**Status:** PASS (`clean`)

## Summary

The latest delegated-authorization implementation correctly places service-layer checks on the inspected role/account mutation paths: non-ADMIN actors cannot mutate a role they hold, grant permission IDs outside their live set, assign or migrate users into roles with a broader active-permission set, or create/promote/demote an ADMIN identity. The ADMIN paths remain explicit and the newly requested JDBC/unit tests pass 14/14.

HI-09 is now resolved by extending the role-transaction fixture with the minimal `users` table and a seeded ADMIN actor. Both transaction cases reach and satisfy their original rollback/concurrency assertions; no production bypass or new BLOCKER/HIGH issue was found.

Latest verification: `mvn -f core/pom.xml -Dtest=RoleAdministrationServiceTransactionTest,RoleAdministrationServiceJdbcTest,PlatformAccountServiceTest test` passed 16/16 with zero failures/errors/skips. `mvn -f core/pom.xml test` passed all 457 tests with zero failures/errors and 18 environment-gated skips. Earlier frontend, MySQL/Flyway, Playwright, and obligation-JSON results are unchanged by this targeted rereview.

## Claude Follow-up Rereview Disposition

### CL-HI-01: Oversized public-login username could reach persistence

**Original classification:** HIGH
**Disposition:** RESOLVED. `LoginRequest.java:6-7` applies `@Size(max = 20)` under the controller's existing `@Valid`; `LoginRequestValidationTest.java:10-19` proves 21 characters are rejected; and `LoginPage.tsx:64-72` mirrors the 20-character boundary. The focused test and full suite pass.

### CL-HI-02: Identity SQL and Flyway migrations lacked target-MySQL execution

**Original classification:** HIGH
**Disposition:** RESOLVED. `Phase05IdentityMySqlIntegrationTest.java:40-141` starts the existing digest-pinned disposable MySQL 8.4 fixture, lets Spring/Flyway apply the complete V1/V1200-V1402 chain, performs a BCrypt-backed unusual successful login through `AuthService`, verifies `user_sessions`, `login_history`, and `identity_notification_outbox`, checks `isActive`, exact-`jti` revocation, current version 1402, and `validateWithResult()`. The integration command independently passed 1/1 with zero owned Docker residuals.

### CL-IN-01: Platform account could alter its own identity or role membership

**Original classification:** INFORMATIONAL
**Disposition:** RESOLVED. `PlatformAccountService.java:86-95` rejects `userId == actorUserId` before validation or persistence, and `PlatformAccountServiceTest.java:145-159` proves neither account lookup nor role replacement occurs.

### CL-IN-02: Account-overview aggregation lacked direct database coverage

**Original classification:** INFORMATIONAL
**Disposition:** RESOLVED. `RoleAdministrationServiceJdbcTest.java:103-115` verifies current identity, role, login metadata, and MENU/BUTTON/API/DATA grants from the JDBC fixture.

### CL-IN-03: Protected-phone failure and 403 response-shape behavior were implicit

**Original classification:** INFORMATIONAL
**Disposition:** RESOLVED BY DOCUMENTED CONTRACT. `DECISIONS.md:31-41` records fail-closed unreadable phone behavior and declares HTTP status, rather than a uniform response body, as the 403 client contract. This matches the inspected service/security/frontend behavior and the revised test matrix; no contradictory dependency on a 403 body was found.

## Latest Claude BLOCKER Fix Rereview

- **Non-ADMIN self-role mutation:** RESOLVED in the inspected paths. `RoleAdministrationService.java:71-76,149,167-170,209` rejects membership replacement and mutation/update/delete of a role currently held by the actor; the JDBC test covers permission replacement on the actor's own role.
- **Permission amplification:** RESOLVED. `RoleAdministrationService.java:171-186,327-349` validates active permission IDs and requires every requested permission to be in the non-ADMIN actor's current active grant set.
- **Over-privileged assignment/migration:** RESOLVED. `RoleAdministrationService.java:66-76,199-212,314-349` locks the target platform roles, validates their active status, and bounds their active permissions by the actor's current set before membership replacement or migration.
- **ADMIN identity creation/promotion/demotion:** RESOLVED. `PlatformAccountService.java:67-82,87-118` invokes `assertPlatformAccountTypeGrantAllowed` for creation and whenever either the existing or requested type is ADMIN; `RoleAdministrationService.java:236-240` denies non-ADMIN actors. This also prevents a delegated actor from demoting an ADMIN account.
- **ADMIN normal path:** No code regression found. The ADMIN bypass is explicit, and the JDBC/unit success paths still exercise role assignment, role update/deletion/migration, account create/update, and ADMIN grant allowance. The requested `RoleAdministrationServiceJdbcTest` and `PlatformAccountServiceTest` pass 14/14.

## HI-09 Final Rereview Disposition

### HI-09: The new administrator lookup breaks both role-transaction proofs and the required backend suite

**Classification:** HIGH
**Final disposition:** RESOLVED. `RoleAdministrationServiceTransactionTest.java:30-50` now creates the minimal `users(id, user_type)` fixture and seeds actor `99` as ADMIN. The focused run passes both the audit-failure rollback case and the concurrent assignment/delete serialization case, confirming execution reaches their postcondition assertions. The full required Maven suite also passes.
**File:** `core/src/test/java/com/ycsopen/sms/core/service/account/RoleAdministrationServiceTransactionTest.java:30-48`
**Triggering production call:** `core/src/main/java/com/ycsopen/sms/core/service/account/RoleAdministrationService.java:209,308-324`
**Failed cases:** `core/src/test/java/com/ycsopen/sms/core/service/account/RoleAdministrationServiceTransactionTest.java:50-60,62-89`
**Issue:** The new self-role guard calls `isAdministrator`, which executes `SELECT user_type FROM users WHERE id = ?` on every delete path. The transaction-test fixture creates `roles`, `user_roles`, `role_permissions`, and `role_change_history`, but no `users` table or ADMIN actor row. Both tests therefore fail with `BadSqlGrammarException: Table "USERS" not found` before reaching the intended audit-failure rollback and concurrent assignment/delete behavior. This is not an optional environment skip: it reproduces in the focused command and makes `mvn -f core/pom.xml test` fail with 457 tests run, 1 failure, 1 error, and 18 skips. Consequently the phase's atomic migration/concurrency evidence is currently non-executable.

**Minimal fix:** Extend `RoleAdministrationServiceTransactionTest.setUp()` with the minimal `users` schema needed by `isAdministrator` and seed actor `99` as ADMIN (or build the fixture from the actual migration schema). If delegated-actor transaction cases are added, also create/seed `permissions`. Then rerun the transaction test and the full Maven suite and require zero failures/errors before restoring PASS.

## Current Unresolved Findings

None.

## Final MD-08 Rereview Disposition

### MD-08: Protected-phone and account-transaction evidence exceeded the named executable tests

**Classification:** WARNING
**Final rereview disposition:** RESOLVED. `PlatformAccountPhoneStoreTest.java:38-67` now proves the real codec/JDBC/publication-fence/migration-AAD/masking path, while `PlatformAccountServiceTransactionTest.java:31-71` proves rollback through the Spring proxy. `TEST-MATRIX.md:7,19` and the two obligation JSON records name those exact suites and no longer overstate the boundary. Those focused tests and the latest full Maven suite pass.
**File:** `.planning/phases/05-console-identity-platform-rbac/TEST-MATRIX.md:7,19`
**Related evidence:** `.planning/phases/05-console-identity-platform-rbac/EVIDENCE/OBL-F-1-1-A.json:1`, `.planning/phases/05-console-identity-platform-rbac/EVIDENCE/OBL-FIELD-ACCOUNT-PHONE.json:1`
**Test boundary:** `core/src/test/java/com/ycsopen/sms/core/service/account/PlatformAccountServiceTest.java:38-68`, `core/src/test/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldContextsTest.java:11-26`
**Issue:** The matrix says `PlatformAccountServiceTest` verifies protected phone storage and transaction behavior, and the phone evidence records protected storage, Phase 3 AAD compatibility, and masking as PASS. That test mocks `PlatformAccountPhoneStore`, `UserRepository`, and `JdbcTemplate`, constructs `PlatformAccountService` directly rather than through a Spring proxy, stubs the masked result, and only verifies delegation. `ProtectedFieldContextsTest` proves exact AAD value equality, but no Phase 05 test instantiates the real `PlatformAccountPhoneStore` at `PlatformAccountPhoneStore.java:37-86`, persists an envelope, reads/masks it, or proves rollback when a later create step fails. The implementation trace is internally consistent, so this is an evidence/test-reliability gap rather than a demonstrated protection bypass.
**Minimal fix:** Add a real-codec/JDBC store-and-mask round-trip using the migration context and a proxied transaction test that injects a failure after account/phone persistence and asserts all writes roll back. Alternatively, narrow the matrix and JSON PASS wording to delegation plus AAD-constant coverage until those tests exist.

## First Rereview Findings and Second Rereview Disposition

### HI-02-R: Broad login history still bypasses the API permission

**Classification:** BLOCKER (HIGH tier)
**Second rereview disposition:** RESOLVED. `LoginHistoryController.java:31-40` now requires ADMIN or both `identity:history:read` and `identity:history:all`; `LoginHistoryControllerTest.java:33-52` rejects DATA-only and API-only callers and accepts the conjunction.
**Original issue/fix:** The first rereview found DATA authority alone enabled broad history. Require the API+DATA conjunction and negative tests.

### HI-08-R: Acceptance evidence still substitutes fixtures for the claimed backend behavior

**Classification:** BLOCKER (HIGH tier)
**Second rereview disposition:** RESOLVED for the HIGH finding. `TEST-MATRIX.md:3-27` now explicitly limits fixture Chrome evidence to rendered UI behavior and binds backend claims to executable Java suites; all 21 obligation JSON files exist and parse. The narrower MD-08 overstatement identified in that rereview was subsequently closed by the final tests described above.
**Original issue/fix:** The first rereview found the matrix using fixture routes as backend/security proof. Relabel fixture coverage and attach server claims to executable backend tests.

### NH-01: Platform-only console gate prevents tenant logout from revoking its session

**Classification:** BLOCKER (HIGH tier)
**Second rereview disposition:** RESOLVED. `SecurityConfig.java:50` allows the exact authenticated logout route before the platform-only catch-all, and `JwtSecurityBoundaryTest.java:111-120` sends a tenant JWT through the filter chain and verifies revocation of that issued session ID and user.
**Original issue/fix:** The first rereview found tenant logout rejected before durable revocation. Exempt only the exact authenticated logout endpoint and test exact-`jti` revocation.

### NH-02: Concurrent role assignment and deletion can leave a dangling user-role row

**Classification:** BLOCKER (HIGH tier)
**Second rereview disposition:** RESOLVED. `RoleAdministrationService.java:60-76,133-177,180-215,267-280` applies the same sorted `SELECT ... ORDER BY id FOR UPDATE` discipline to assignment, role update, permission replacement, and deletion. With HI-09's fixture repair, `RoleAdministrationServiceTransactionTest.java:65-93` again exercises deletion serialization against an already-locked assignment and verifies migration of the concurrent association.
**Original issue/fix:** The first rereview found assignment/deletion validation used non-locking reads without an FK. Serialize all competing role operations on consistently ordered role rows and add a concurrency test.

### MD-07: Role audit snapshots omit a mutable field

**Classification:** WARNING
**Second rereview disposition:** RESOLVED. `RoleAdministrationService.java:128-152,248-285` includes description in create/update/current snapshots, and `RoleAdministrationServiceJdbcTest.java:88-94` proves a description-only mutation creates distinct complete snapshots.
**Original issue/fix:** The first rereview found description-only changes invisible in before/after audit state. Include every mutable field and test that mutation.

### MD-03-R: Validation and TypeScript contracts still exceed or deny the database's nullable field contract

**Classification:** WARNING
**Second rereview disposition:** RESOLVED. DTO/service/UI limits are now real name 50 and email 100; nullable response fields are modeled and normalized; `PlatformAccountPolicyTest` covers width overflow.
**Original issue/fix:** The first rereview found API widths exceeded MySQL and frontend non-null types contradicted legal rows. Align all three boundaries and add limits.

### MD-06-R: Successful permission save can restore the stale pre-save draft

**Classification:** WARNING
**Second rereview disposition:** RESOLVED. `RoleManagementPage.tsx:57-73` commits submitted permission IDs to the selected-role cache/draft before invalidation, then adopts the confirmed server response; `identity-pages.test.tsx:266-284` verifies save, refetch, checked state, and clean/disabled save.
**Original issue/fix:** The first rereview found save success could reinitialize from stale cache. Keep draft identity stable and reconcile after refetch.

## Original Findings and Rereview Disposition

### Original Blockers

### BL-01: Any authenticated account can call unrelated privileged console APIs

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. `SecurityConfig` blocks tenant identities from platform console routes, controller guards restrict privileged operations, and the exact logout exception is authenticated and covered by the tenant-session regression test.
**File:** `core/src/main/java/com/ycsopen/sms/core/config/SecurityConfig.java:43-50`
**Supporting call sites:** `core/src/main/java/com/ycsopen/sms/core/web/controller/ChannelController.java:22-47`, `core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java:27-82`
**Issue:** The new security boundary applies only `.authenticated()` to every `/api/v1/console/**` request. `JwtAccessVerifier` authenticates platform and tenant user types, while existing channel and tenant administration controllers have no `@PreAuthorize` checks. Consequently, any valid tenant user, operator, or finance user can directly create/pause/resume channels and approve/reject tenants. `AdminLayout` also renders nearly every non-identity admin link to every platform type (`web/src/components/layout/AdminLayout.tsx:7-23`), but direct API access makes this a server-side authorization bypass even if the UI is hidden.
**Minimal fix:** Default-deny console routes by authority and add explicit method authorization to every console controller before enabling the global matcher. Add integration tests using real `JwtAccessVerifier` results for `TENANT_USER`, `OPERATOR`, and `FINANCE` against every privileged mutation, asserting 403 and no service/repository call.

### BL-02: Failed-login counters, lock state, and history are rolled back on every rejection

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. Both public login overloads use `noRollbackFor = BusinessException.class`, the lookup takes a pessimistic write lock, and `AuthServiceTransactionTest` verifies five persisted failures/history rows plus a concurrent five-attempt case through the Spring proxy.
**File:** `core/src/main/java/com/ycsopen/sms/core/service/account/AuthService.java:46-79`
**Test masking the defect:** `core/src/test/java/com/ycsopen/sms/core/service/account/AuthServiceTest.java:30-42`
**Issue:** `login` is transactional, writes the failure count/status and `login_history`, then throws `BusinessException`, which extends `RuntimeException`. Spring rolls the transaction back by default. This affects unknown users (line 50), locked/disabled/expired attempts (lines 55-68), and bad passwords (lines 72-79). In production, the fifth bad password does not persist `LOCKED`, and none of the rejected attempts remain in login history. The Mockito test observes mutation of an in-memory `User` and calls the service directly, so it never exercises transaction rollback.
**Minimal fix:** Commit the rejection mutation before surfacing the error—for example, move failure recording/atomic lock update into a separate proxied `REQUIRES_NEW` service, or return a rejection result from the transaction and throw after it commits. Add a Spring/JDBC integration test that performs five rejected logins through the proxied service/controller and then queries both `users` and `login_history` in a new transaction.

### BL-03: Phone encryption AAD is incompatible with the Phase 03 data contract

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. Migration and live access share `ProtectedFieldContexts`; `ProtectedFieldContextsTest` proves the exact platform and tenant context values; and the final `PlatformAccountPhoneStoreTest` exercises the real codec/store round trip with migration AAD.
**File:** `core/src/main/java/com/ycsopen/sms/core/service/account/PlatformAccountPhoneStore.java:90-100`
**Supporting contract implementation:** `core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationRunner.java:446-452`
**Issue:** Phase 03 protects `users.phone_encrypted` with logical owner `crypto-storage-bootstrap` and resource identity `id={id}`. Phase 05 uses `platform-account-service` and `user_id={id}`. Both values are authenticated AAD, so existing phone envelopes produced by the migration cannot be decrypted by `masked`, and envelopes written by Phase 05 are outside the established context used by migration/current crypto tooling. No Phase 05 test instantiates the real phone store; `PlatformAccountServiceTest` mocks it at line 39.
**Minimal fix:** Reuse one canonical context factory for `users.phone_encrypted` with `crypto-storage-bootstrap`, table `users`, field `phone_encrypted`, tenant scope derived consistently from `tenant_id`, and identity `id={id}`. Add a real-codec compatibility test that decrypts a migration-produced envelope through `PlatformAccountPhoneStore` and vice versa.

### Original High Severity

### HI-01: React Query cache survives logout and discloses the previous account's protected data

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. Protected keys now include subject/session identity, the shared client is cleared on session establishment/change and logout/401/expiry, and the two-principal unit test verifies both key separation and cache removal.
**File:** `web/src/store/authStore.ts:150-156`
**Related files:** `web/src/pages/admin/identity/useIdentityAccess.ts:8-17`, `web/src/pages/admin/identity/UserManagementPage.tsx:80-81`, `web/src/pages/admin/identity/AccountOverviewPage.tsx:12-17`
**Issue:** Logout clears only Zustand/session storage; it never clears the application-wide React Query client. Identity queries use principal-independent keys such as `['account-overview']` and `['platform-accounts']`. After account A logs out and account B logs in, cached identity, IP address, roles, permissions, account lists, and history can render under B. The global query client has a 30-second `staleTime` (`web/src/main.tsx:7-9`), so a prompt re-login can use A's data without even refetching; after staleness, cached data still remains renderable during background fetch. Cached ADMIN overview data can also make `access.can` return true for the new lower-privilege user.
**Minimal fix:** Clear/remove all protected queries synchronously on logout, 401, expiry, and principal change, and include an immutable current subject/session identity in protected query keys. Add a two-user test using one `QueryClient` that loads A, logs out, logs in as B, and asserts that no A data or A-derived controls ever render.

### HI-02: Login history is all-or-nothing, exposing every user's IP/client data while denying ordinary users their own history

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED in the second rereview. Own history is the default; other-user access is denied; broad history requires ADMIN or the API+DATA conjunction, with negative tests for each authority alone.
**File:** `core/src/main/java/com/ycsopen/sms/core/web/controller/LoginHistoryController.java:22-28`
**Related UI:** `web/src/pages/admin/identity/AccountOverviewPage.tsx:12-17`
**Issue:** Possessing `identity:history:read` allows an arbitrary optional `userId`, including no filter, and therefore returns all usernames, IP addresses, user agents, and outcomes. There is no DATA-scope authority for broad history. Conversely, a signed-in user without that authority cannot query their own ID, so the account-overview history request receives 403. This contradicts the own-history/default and privileged-broader-history contract and creates a confidentiality leak for any non-admin granted the menu/API permission.
**Minimal fix:** Derive the subject from `Authentication`; allow ordinary callers only an implicit own-history query, and require ADMIN plus a distinct active DATA permission such as `identity:history:all` for arbitrary/all-user queries. Add controller integration tests for own, other-user, and unfiltered access.

### HI-03: Password replacement neither clears expiry nor revokes existing sessions

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. Nonblank password replacement clears expiry and marks every active durable session revoked in the same service transaction; the unit test asserts both effects.
**File:** `core/src/main/java/com/ycsopen/sms/core/service/account/PlatformAccountService.java:86-106`
**Issue:** Updating a password only replaces `passwordHash`. If the old password has expired, `passwordExpireTime` remains in the past, so the account still cannot authenticate with the new password. Existing durable sessions also remain valid after a credential reset because no session revocation occurs. This breaks recovery from password expiry and leaves compromised sessions usable after an administrator changes the password.
**Minimal fix:** When a nonblank password is supplied, set the next expiry according to the configured policy (or clear it when null means no expiry) and mark all active sessions for that user revoked in the same transaction. Test both a previously expired account and an already-open session.

### HI-04: The catch-all exception advice converts authorization and validation failures into HTTP 500

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. Dedicated 403 and 400 handlers precede the catch-all, and MockMvc verifies forbidden, invalid-body, and safe correlated-500 responses.
**File:** `core/src/main/java/com/ycsopen/sms/core/common/exception/GlobalExceptionHandler.java:27-40`
**Related tests:** `core/src/test/java/com/ycsopen/sms/core/web/controller/PlatformAccountControllerAuthorizationTest.java:35-40`, `core/src/test/java/com/ycsopen/sms/core/common/exception/GlobalExceptionHandlerTest.java:18-37`
**Issue:** The generic `@ExceptionHandler(Exception.class)` also matches method-security `AccessDeniedException` and MVC validation exceptions. Thus an authenticated forbidden call resolved by controller advice becomes the safe-but-wrong 500 response, and malformed account/role DTOs also become 500. The authorization tests call proxied controller methods directly and stop at the thrown exception; the exception test checks only a `RuntimeException`, so neither verifies the HTTP boundary. The frontend consequently raises the internal-error notice instead of preserving the session and showing the required 403 message.
**Minimal fix:** Add specific handlers (or delegate back to Spring Security) for `AccessDeniedException` as 403 and validation/binding exceptions as 400, leaving the catch-all only for genuinely unexpected failures. Verify with full-filter-chain MockMvc requests.

### HI-05: Account state changes physically delete durable sessions instead of revoking them

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. State transitions now update `revoked_at` with `COALESCE` and retain the durable row; the unit test asserts the exact update.
**File:** `core/src/main/java/com/ycsopen/sms/core/service/account/AccountStateService.java:36-41`
**Migration contract:** `core/src/main/resources/db/migration/V1400__console_identity_platform_rbac.sql:2-3`
**Issue:** V1400 adds `revoked_at` to preserve durable session/revocation evidence, and logout uses it, but disable/enable/unlock execute `DELETE FROM user_sessions`. This destroys login/session audit data and makes state-change revocation inconsistent with the durable lifecycle promised by the design. The test explicitly verifies the destructive SQL (`AccountStateServiceTest.java:35-38`) rather than the requirement.
**Minimal fix:** Replace the delete with `UPDATE user_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP) WHERE user_id = ? AND revoked_at IS NULL`, preserving rows and making the transition idempotently auditable.

### HI-06: Mutation authorization collapses BUTTON and API permissions into the same authority

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED in code. V1402 adds distinct mutation API authorities and controllers require those authorities (plus account DATA scope where applicable); method-security tests cover representative list/grant paths.
**File:** `core/src/main/resources/db/migration/V1401__platform_identity_permissions.sql:6-15`
**Related controllers:** `core/src/main/java/com/ycsopen/sms/core/web/controller/PlatformAccountController.java:40-72`, `core/src/main/java/com/ycsopen/sms/core/web/controller/PlatformRoleController.java:41-67`
**Issue:** V1401 creates only read operations as `API`; all create/update/grant/delete codes are typed `BUTTON`, and the controllers use those BUTTON codes as the sole mutation authority. A principal granted UI button visibility automatically receives direct API mutation access, while there is no independently assignable API permission for these endpoints. This does not implement four-granularity RBAC and prevents least-privilege separation between interface access and UI controls.
**Minimal fix:** Add a forward Flyway migration with distinct API permissions for every mutation route/method, retain BUTTON permissions for visibility, and require both the appropriate API authority and any applicable DATA scope server-side.

### HI-07: Role mutations have no authenticated actor or audit history

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED in the second rereview. Actor/audit attribution and rollback remain covered, and descriptions are now present in before/after snapshots with a description-only regression test.
**File:** `core/src/main/java/com/ycsopen/sms/core/web/controller/PlatformRoleController.java:41-67`
**Related service:** `core/src/main/java/com/ycsopen/sms/core/service/account/RoleAdministrationService.java:99-195`
**Issue:** Create, update, permission replacement, migration, and deletion never accept `Authentication`/actor ID and never write an audit record. Migration uses the literal `role-migration` as `granted_by` (lines 183-190), which cannot identify the administrator. The phase goal explicitly requires auditable role operations, especially destructive migration.
**Minimal fix:** Pass the authenticated subject into every mutation, persist actor/action/role/before-after metadata in a role audit table in the same transaction, and use that actor as `granted_by` during migration.

### HI-08: The claimed acceptance evidence replaces the backend behavior with fixtures

**Classification:** BLOCKER
**Rereview disposition:** RESOLVED. The matrix explicitly scopes fixture Playwright to UI behavior and binds backend claims to Java tests; the final phone-store and proxied-transaction tests close the former MD-08 test-to-claim mismatch.
**File:** `web/test/scripts/identity.spec.ts:36-65`
**Related evidence:** `.planning/phases/05-console-identity-platform-rbac/TEST-MATRIX.md:7-27`, `web/test/scripts/identity.spec.ts:287-316`
**Issue:** The matrix claims browser cases prove BCrypt persistence, state attribution/revocation, transactional role migration, current database permissions, login history/outbox behavior, and real safe 500 handling. In reality, `mockIdentityApis` fulfills all identity requests without the backend, many obligation tests merely fill an input or observe fixture text, and the “real” 500 comes from a bespoke Node server that does not exercise `GlobalExceptionHandler`. The role JDBC test constructs H2 tables manually and calls a non-proxied service, so it does not test Flyway or transactional rollback. This allows BL-02, BL-03, HI-04, and HI-05 to pass undetected.
**Minimal fix:** Reclassify fixture browser tests as UI contract tests. Add target-MySQL/Spring integration cases for security, migration, transaction, crypto, and audit obligations, and run Playwright against the real backend for the direct 401/403, revocation, live-permission, and injected-500 cases before recording obligation evidence.

### Original Medium Severity

### MD-01: Failed-login increment remains race-prone once rollback is fixed

**Classification:** WARNING
**Rereview disposition:** RESOLVED. `findByUsernameForUpdate` takes a pessimistic row lock and the concurrent proxied integration test preserves all five increments.
**File:** `core/src/main/java/com/ycsopen/sms/core/service/account/AuthService.java:48-79`
**Issue:** The user row is read without a pessimistic lock or version column, then the count is incremented in memory and saved. Concurrent bad-password requests can read the same count and overwrite one another, delaying or bypassing the five-attempt lock threshold.
**Minimal fix:** Combine the increment/threshold transition in an atomic SQL update or lock the user row with `PESSIMISTIC_WRITE`; add a concurrent integration test that proves the threshold under parallel requests.

### MD-02: Password inputs do not account for BCrypt's 72-byte boundary

**Classification:** WARNING
**Rereview disposition:** RESOLVED. Create/update policy and login enforce the UTF-8 byte boundary; DTO/UI add character caps and the policy test exercises multibyte overflow.
**File:** `core/src/main/java/com/ycsopen/sms/core/service/account/PlatformAccountPolicy.java:47-50`
**Related DTO:** `core/src/main/java/com/ycsopen/sms/core/web/dto/LoginRequest.java:5`
**Issue:** The policy has only a minimum length, and login has no maximum. BCrypt uses only a bounded password byte sequence, so overlong UTF-8 passwords can have ignored suffixes or behave inconsistently across implementations. The UI regex also accepts unbounded input (`web/src/pages/admin/identity/UserManagementPage.tsx:137-139`).
**Minimal fix:** Define one server-side maximum in UTF-8 bytes compatible with the chosen BCrypt implementation, enforce it on create/update/login, mirror it with UI `maxLength`, and test multibyte boundary values.

### MD-03: Required identity fields are enforced only by the browser and frontend types deny backend nullability

**Classification:** WARNING
**Rereview disposition:** RESOLVED in the second rereview. Database/API/UI widths and response nullability are aligned and covered by policy/UI tests.
**File:** `core/src/main/java/com/ycsopen/sms/core/web/dto/PlatformAccountCreateRequest.java:9-17`
**Related files:** `core/src/main/java/com/ycsopen/sms/core/web/dto/PlatformAccountUpdateRequest.java:9-17`, `web/src/api/identity.ts:8-20`
**Issue:** The contract requires a real name, but both backend DTOs accept null/blank `realName`; email is also nullable in the entity/response. The TypeScript contract declares `email`, `realName`, `maskedPhone`, `createdBy`, and `createdAt` as non-null strings, and the edit form feeds those values directly into controlled inputs. Direct clients can create nameless accounts, and legacy/null database rows violate the React contract.
**Minimal fix:** Add the intended server constraints and length limits for required fields, or explicitly model optional response fields as nullable and normalize them before form binding. Add direct API validation tests rather than relying on HTML `required`.

### MD-04: Account and overview responses cannot satisfy their production data contract

**Classification:** WARNING
**Rereview disposition:** RESOLVED. Account responses/tables now include recent login and account overview returns/renders permission grants grouped by MENU/BUTTON/API/DATA.
**File:** `core/src/main/java/com/ycsopen/sms/core/web/dto/PlatformAccountResponse.java:8-19`
**Related UI:** `web/src/pages/admin/identity/UserManagementPage.tsx:190-201`, `web/src/pages/admin/identity/AccountOverviewPage.tsx:34-38`
**Issue:** The platform-account response/table omit the required recent-login column even though `User.lastLoginTime` exists. Account overview returns only untyped permission-code strings, and the UI renders one unstructured list rather than MENU/BUTTON/API/DATA groups. The current API shape cannot support the required grouped scope without another lookup/guess.
**Minimal fix:** Add safe last-login data to the account projection and return permission summaries grouped or tagged by `resourceType`; render the specified column and four groups.

### MD-05: Dialogs can discard dirty changes and omit the required keyboard/focus behavior

**Classification:** WARNING
**Rereview disposition:** RESOLVED in implementation. The shared modal supplies initial focus, trap, Escape, and opener restoration, while account/role/permission dirty exits use confirmation. Dedicated interaction tests are still absent but no current defect was reproduced from inspection.
**File:** `web/src/pages/admin/identity/UserManagementPage.tsx:221-266`
**Related file:** `web/src/pages/admin/identity/RoleManagementPage.tsx:180-217`
**Issue:** “放弃账号修改” closes immediately without the required confirmation; Escape handling, initial focus, focus trap, and focus restoration are absent from account, role, disable, delete, and migration dialogs. The permission “放弃权限修改” action also resets immediately (`RoleManagementPage.tsx:171-173`) despite the explicit confirmation contract. This causes real data loss for keyboard and pointer users and makes the modal semantics incomplete.
**Minimal fix:** Use a shared dialog implementation with focus management and Escape handling; track dirty state and route every dirty close/abandon path through the specified confirmation before resetting state.

### MD-06: Background role refetch can silently erase unsaved permission edits

**Classification:** WARNING
**Rereview disposition:** RESOLVED in the second rereview. Save commits the submitted IDs before invalidation, adopts the confirmed response, and the unit test proves the refetched draft remains clean.
**File:** `web/src/pages/admin/identity/RoleManagementPage.tsx:38-47`
**Issue:** Every change to `roles.data` unconditionally copies persisted permission IDs into local edit state. React Query can refetch on focus/reconnect, so an unrelated background refresh replaces dirty checkbox edits without the required warning. This is independent of the explicit role-selection confirmation.
**Minimal fix:** Initialize local permissions only when the selected role identity changes or when a save/discard explicitly completes; if refreshed server data conflicts while dirty, retain the draft and surface a conflict/confirm path.

---

_Reviewed: 2026-09-07T02:27:47Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
