---
phase: 14-auditable-exemption-policy
reviewed: 2026-09-09T02:08:55Z
re_reviewed: 2026-09-09T02:19:09Z
depth: deep
files_reviewed: 17
files_reviewed_list:
  - core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/ExemptionPolicyController.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ExemptionPolicyCreateRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ExemptionPolicyPreviewRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ExemptionPolicyPreviewResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ExemptionPolicyResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ExemptionPolicyRevokeRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ExemptionPolicyUsageResponse.java
  - core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql
  - core/src/test/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyServiceTest.java
  - web/src/api/exemptionPolicyApi.ts
  - web/src/pages/admin/exemptions/ExemptionPolicyPage.tsx
  - web/src/styles/exemption-policy.css
  - web/src/components/layout/AdminLayout.tsx
  - web/src/router/routes.tsx
  - web/test/unit/exemption-policy.test.tsx
  - web/test/scripts/exemption-policy.spec.ts
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
---

# Phase 14: Code Review Report

**Reviewed:** 2026-09-09T02:08:55Z
**Depth:** deep
**Files Reviewed:** 17
**Status:** issues_found

## Summary

Reviewed the Phase 14 auditable exemption policy implementation across backend service/controller/DTOs, migration, frontend API/page/routing, and tests. The implementation has security and correctness defects in the permission model, overlap precedence, and migration handling for existing exemption rows. Targeted verification run during review: `mvn -q -f core/pom.xml -Dtest=ExemptionPolicyServiceTest test` passed, and `npm --prefix web test -- exemption-policy.test.tsx` passed; those tests do not cover the blocking cases below.

## Narrative Findings (AI reviewer)

## Blockers

### BL-01: Exemption endpoints bypass the required read/write/audit permissions

**Severity:** BLOCKER
**File:** `core/src/main/java/com/ycsopen/sms/core/web/controller/ExemptionPolicyController.java:15`
**Issue:** The controller grants every endpoint to any `ADMIN` or `OPERATOR` with a single class-level `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`. Phase 14's UI/API contract requires separate `exemption:write`, `exemption:read`, and `exemption:audit` boundaries, but the migration only alters tables and creates audit tables; it never inserts exemption permissions into the permission catalog (`core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:1`). The UI repeats the same role-only assumption at `web/src/pages/admin/exemptions/ExemptionPolicyPage.tsx:42`, and the nav exposes the menu by broad role at `web/src/components/layout/AdminLayout.tsx:20`. Result: an operator without exemption authority can create, revoke, preview, and read audit history.
**Fix:** Add Phase 14 permission rows, then enforce them per endpoint and UI affordance. For example:

```java
@GetMapping
@PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:read')")
public ApiResponse<List<ExemptionPolicyResponse>> list() { ... }

@PostMapping
@PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:write')")
public ApiResponse<ExemptionPolicyResponse> create(...) { ... }

@PostMapping("/{id}/revoke")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:write')")
public ApiResponse<ExemptionPolicyResponse> revoke(...) { ... }

@GetMapping("/usage-history")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:audit')")
public ApiResponse<List<ExemptionPolicyUsageResponse>> usageHistory() { ... }
```

Also insert permission catalog records such as `exemption:menu`, `exemption:read`, `exemption:write`, and `exemption:audit`, then gate `AdminLayout` and `ExemptionPolicyPage` through `useIdentityAccess` instead of `userType`.

### BL-02: Wildcard overlap ordering can make an unauthorized specific exemption effective

**Severity:** BLOCKER
**File:** `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:116`
**Issue:** Preview selects matching exact-or-wildcard candidates and orders only by `version_no DESC, id DESC` (`ExemptionPolicyService.java:116-123`), then applies the decision solely to `candidates.getFirst()` (`ExemptionPolicyService.java:128-148`). Because `version_no` is scoped by exact `(tenant,type,resource,product,scope)` at `ExemptionPolicyService.java:221-225`, an approved wildcard rule inserted after a pending/rejected/revoked resource-specific rule has the same version number and a higher id, so it wins and returns `ACTIVE`. That violates the "bounded exemptions" and "unauthorized" precedence requirements.
**Fix:** Define and encode explicit overlap precedence before status evaluation. Resource/product/scope exact matches should outrank wildcards, and a more-specific unauthorized/revoked/expired rule should not be bypassed by a broader approved rule unless the product explicitly allows that. One concrete query shape:

```sql
SELECT * FROM exempt_rules
WHERE tenant_id=? AND exempt_type=?
  AND (resource_id=? OR resource_id='*')
  AND (product_code=? OR product_code='*')
  AND (scope=? OR scope='*')
ORDER BY
  CASE WHEN resource_id=? THEN 0 ELSE 1 END,
  CASE WHEN product_code=? THEN 0 ELSE 1 END,
  CASE WHEN scope=? THEN 0 ELSE 1 END,
  version_no DESC,
  id DESC
```

Add regression tests for exact pending/revoked rules overlapped by later wildcard-approved rules, and for wildcard fallback when no specific rule exists.

### BL-03: Migration leaves legacy unbounded rows that crash list/preview

**Severity:** BLOCKER
**File:** `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:1`
**Issue:** `exempt_rules` already exists with nullable `scope` and nullable `valid_until` (`core/src/main/resources/db/migration/V1__init_schema.sql:236-245`). The Phase 14 migration adds `valid_from` and other columns but does not backfill or make `valid_until` non-null (`V2300__auditable_exemption_policy.sql:1-14`). The service now assumes `valid_until` is always present and calls `rs.getTimestamp("valid_until").toLocalDateTime()` at `ExemptionPolicyService.java:208`. Any pre-existing exemption row with `valid_until IS NULL` causes list and preview paths to throw before an operator can inspect or fix it.
**Fix:** Backfill legacy rows and tighten the schema in the same migration, or make the service explicitly handle legacy unbounded rows as denied until remediated. A schema-side fix is:

```sql
UPDATE exempt_rules SET scope = '*' WHERE scope IS NULL OR scope = '';
UPDATE exempt_rules SET valid_until = '9999-12-31 23:59:59' WHERE valid_until IS NULL;

ALTER TABLE exempt_rules
    MODIFY scope VARCHAR(255) NOT NULL DEFAULT '*',
    MODIFY valid_until DATETIME NOT NULL;
```

If unbounded legacy exemptions must not survive Phase 14, set them to `REJECTED` or `revoked_at` during migration and record a migration audit entry.

## High Severity

### HI-01: Version numbers are not concurrency-safe, breaking audit identity

**Severity:** HIGH
**File:** `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:221`
**Issue:** `nextVersion` computes `MAX(version_no) + 1` without a uniqueness constraint or lock (`ExemptionPolicyService.java:221-225`), and the migration does not add a unique key for `(tenant_id, exempt_type, resource_id, product_code, scope, version_no)` (`V2300__auditable_exemption_policy.sql:1-14`). Concurrent creates for the same exemption key can both insert the same `version_no`, which makes audit history ambiguous and can change preview ordering based on auto-increment id rather than the version being audited.
**Fix:** Add a unique constraint on the exemption identity plus `version_no`, and allocate versions under a lock or retry on duplicate key. For MySQL, one straightforward approach is selecting matching rows `FOR UPDATE` in the transaction before computing the next version, then retaining a duplicate-key retry as a guard.

### HI-02: The preview UI cannot preview most of the required subject boundary

**Severity:** HIGH
**File:** `web/src/pages/admin/exemptions/ExemptionPolicyPage.tsx:30`
**Issue:** The preview payload hard-codes tenant, type, product, scope, and reason in `INITIAL_PREVIEW` (`ExemptionPolicyPage.tsx:30-38`). The rendered preview panel only lets the operator change resource and control (`ExemptionPolicyPage.tsx:160-169`). That means the UI cannot preview content or account exemptions, cannot preview a different tenant/product/scope, and cannot verify out-of-scope behavior for the operator's actual input, despite the Phase 14 UI contract requiring subject type/id, tenant, product, scope, control code, actor, and reason.
**Fix:** Add preview controls for tenant, exemption type, product, scope, and reason, or let the operator select a policy row and derive all preview fields from that row while still allowing control-code changes. Extend the unit and Playwright tests to cover `CONTENT`, `ACCOUNT`, and out-of-scope preview payloads.

## Medium Severity

### ME-01: Null request bodies can produce 500s instead of domain validation errors

**Severity:** MEDIUM
**File:** `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:49`
**Issue:** `create` partially guards `request == null` for tenant/type, then dereferences `request.resourceId()` at line 49. `preview` repeats the same pattern: it guards tenant/type at lines 104-105, then dereferences `request.resourceId()` at line 106. A missing or malformed JSON body can therefore escape the intended `BusinessException` validation and become an internal server error.
**Fix:** Fail closed at method entry before reading fields:

```java
if (request == null) {
    throw failure("EXEMPTION_REQUEST_REQUIRED", "豁免请求不能为空");
}
```

Apply this to create, preview, and revoke, or consistently use null-safe local extraction for every request field.

### ME-02: Protected exemption data uses unscoped React Query keys

**Severity:** MEDIUM
**File:** `web/src/pages/admin/exemptions/ExemptionPolicyPage.tsx:51`
**Issue:** The page queries policy and audit data with plain keys `['exemption-policies']` and `['exemption-usage-history']` (`ExemptionPolicyPage.tsx:51-58`). The repo defines `protectedQueryKey` specifically so protected data is keyed by principal/session (`web/src/store/authStore.ts:201-205`), and other protected pages use it. This page is now handling cross-tenant exemption and usage-audit data, so using unscoped keys weakens the repo's stale protected-data boundary.
**Fix:** Import `protectedQueryKey` and use it for the policy and usage queries and invalidations:

```ts
const policyKey = protectedQueryKey('exemption-policies');
const usageKey = protectedQueryKey('exemption-usage-history');
```

Use those constants in `useQuery` and `invalidateQueries`.

---

_Reviewed: 2026-09-09T02:08:55Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_

## Re-Review: 2026-09-09T02:17:04Z

**Scope:** Current working tree after fixes, focused on prior findings BL-01, BL-02, BL-03, HI-01, HI-02, ME-01, ME-02 and any new BLOCKER/HIGH introduced by the fixes.

**Verification run during re-review:**

- `mvn -q -f core/pom.xml -Dtest=ExemptionPolicyServiceTest test` passed.
- `npm --prefix web test -- exemption-policy.test.tsx` passed.
- `npm --prefix web test -- identity-pages.test.tsx` passed.

### Prior Finding Disposition

- BL-01: Resolved. The migration now inserts exemption permission catalog rows at `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:22`, controller methods enforce `exemption:read`, `exemption:write`, and `exemption:audit` at `core/src/main/java/com/ycsopen/sms/core/web/controller/ExemptionPolicyController.java:23`, and the UI/nav now use `EXEMPTION_PERMISSIONS` at `web/src/pages/admin/exemptions/ExemptionPolicyPage.tsx:47` and `web/src/components/layout/AdminLayout.tsx:21`.
- BL-02: Resolved. Preview ordering now ranks exact resource/product/scope matches ahead of wildcards at `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:141`, and the regression at `core/src/test/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyServiceTest.java:94` covers a pending specific rule overlapped by a later approved wildcard.
- BL-03: Partially resolved. Null `scope` and `valid_until` values are now backfilled and constrained at `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:1`, but the new unique key can still fail on valid legacy duplicate rows. See RR-BL-01.
- HI-01: Partially resolved. New writes now have a uniqueness constraint plus lock/retry logic at `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:18` and `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:64`, but the migration does not assign distinct versions to existing duplicate rows before adding the key. See RR-BL-01.
- HI-02: Resolved. The preview form now exposes tenant, type, resource, product, scope, control, and reason at `web/src/pages/admin/exemptions/ExemptionPolicyPage.tsx:173`.
- ME-01: Resolved. `create`, `revoke`, and `preview` now reject null request objects before dereferencing at `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:47`, `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:107`, and `core/src/main/java/com/ycsopen/sms/core/service/exemption/ExemptionPolicyService.java:125`.
- ME-02: Resolved. Policy and usage queries now use principal-scoped query keys at `web/src/pages/admin/exemptions/ExemptionPolicyPage.tsx:51`.

## Re-Review Blockers

### RR-BL-01: Unique-key migration can fail on valid legacy exemption rows

**Severity:** BLOCKER
**File:** `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:18`
**Issue:** The original `exempt_rules` table allowed multiple rows with the same `(tenant_id, exempt_type, scope)` and had no `resource_id`, `product_code`, or `version_no` uniqueness (`core/src/main/resources/db/migration/V1__init_schema.sql:236`). The fix adds `resource_id` and `product_code` with defaults and `version_no` with default `1`, then immediately adds `UNIQUE KEY uk_exempt_rules_version (tenant_id, exempt_type, resource_id, product_code, scope, version_no)` at line 18. If production already has two legacy rows for the same tenant/type/scope, both become `resource_id='*'`, `product_code='SMS'`, and `version_no=1`, so the migration fails before the application can deploy.
**Fix:** Split the migration into steps: add the new columns without the unique key, normalize nulls/defaults, assign deterministic distinct `version_no` values to existing duplicate partitions, then add the unique key. For MySQL 8:

```sql
ALTER TABLE exempt_rules
    ADD COLUMN resource_id VARCHAR(128) NOT NULL DEFAULT '*',
    ADD COLUMN product_code VARCHAR(64) NOT NULL DEFAULT 'SMS',
    ADD COLUMN version_no INT NOT NULL DEFAULT 1;

UPDATE exempt_rules target
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, exempt_type, resource_id, product_code, scope
               ORDER BY created_at, id
           ) AS next_version
    FROM exempt_rules
) ranked ON ranked.id = target.id
SET target.version_no = ranked.next_version;

ALTER TABLE exempt_rules
    ADD UNIQUE KEY uk_exempt_rules_version
        (tenant_id, exempt_type, resource_id, product_code, scope, version_no);
```

If legacy duplicates are semantically invalid rather than historical versions, explicitly revoke or reject them during migration and write corresponding audit rows before adding the unique key.

## Final Unresolved Counts

- BLOCKER: 0
- HIGH: 0
- MEDIUM: 0
- LOW: 0
- Total unresolved: 0

_Re-reviewed: 2026-09-09T02:17:04Z_

## Focused Re-Review: 2026-09-09T02:19:09Z

**Scope:** RR-BL-01 only, after the V2300 migration fix.

**Disposition:** Resolved. The migration now adds the new Phase 14 columns without the unique key at `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:4`, assigns deterministic distinct `version_no` values with `ROW_NUMBER()` partitioned by `(tenant_id, exempt_type, resource_id, product_code, scope)` and ordered by `(created_at, id)` at `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:19`, then adds `uk_exempt_rules_version` only after that backfill at `core/src/main/resources/db/migration/V2300__auditable_exemption_policy.sql:30`. Legacy duplicate rows therefore no longer collide at `version_no=1`.

## Final Unresolved Counts After Focused Re-Review

- BLOCKER: 0
- HIGH: 0
- MEDIUM: 0
- LOW: 0
- Total unresolved: 0

_Focused re-review: 2026-09-09T02:19:09Z_
