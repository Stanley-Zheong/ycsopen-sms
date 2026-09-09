---
phase: 15-resource-review-history
reviewed: 2026-09-09T03:21:38Z
depth: deep
files_reviewed: 12
files_reviewed_list:
  - core/src/main/java/com/ycsopen/sms/core/service/review/ResourceReviewHistoryService.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/ResourceReviewHistoryController.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/ResourceReviewHistoryItem.java
  - core/src/main/resources/db/migration/V2400__resource_review_history_permissions.sql
  - core/src/test/java/com/ycsopen/sms/core/service/review/ResourceReviewHistoryServiceTest.java
  - web/src/api/resourceReviewHistoryApi.ts
  - web/src/pages/admin/review/ResourceReviewHistoryPage.tsx
  - web/src/router/routes.tsx
  - web/src/components/layout/AdminLayout.tsx
  - web/src/styles/resource-review-history.css
  - web/test/unit/resource-review-history.test.tsx
  - web/test/scripts/resource-review-history.spec.ts
findings:
  blocker: 1
  high: 1
  medium: 3
  low: 1
  total: 6
unresolved:
  blocker: 0
  high: 0
  medium: 0
  low: 0
  total: 0
status: clean
---

# Phase 15: Code Review Report

**Reviewed:** 2026-09-09T03:21:38Z
**Depth:** deep
**Files Reviewed:** 12
**Status:** clean

## Summary

Reviewed Phase 15 changes after `6822ad89b144a4d5d28805239a1e19cd43c7abf5`, excluding planning artifacts and the unrelated Phase 2 image. Initial review found five issues. Re-review on 2026-09-09T03:04:48Z verified BL-01, HI-01, MD-01, MD-02, and LO-01 are resolved. Re-review on 2026-09-09T03:18:35Z verified the staged direct detail lookup and pageSize cap, with one remaining medium pagination-bound issue. Re-review on 2026-09-09T03:21:38Z verified that remaining offset-overflow finding is resolved by capping `page` at 10000.

Verification executed during review:

- `mvn -f core/pom.xml -Dtest=ResourceReviewHistoryServiceTest test` passed.
- `npm --prefix web test -- resource-review-history.test.tsx` passed.
- `npm --prefix web run build` passed.
- `npm --prefix web exec -- playwright test resource-review-history.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=line` passed.

Additional verification on 2026-09-09T03:18:35Z:

- `mvn -f core/pom.xml -Dtest=ResourceReviewHistoryServiceTest test` passed.
- `npm --prefix web test -- resource-review-history.test.tsx` passed.
- `npm --prefix web run build` passed.
- `npm --prefix web exec -- playwright test resource-review-history.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=line` passed.

Additional verification on 2026-09-09T03:21:38Z:

- `mvn -f core/pom.xml -Dtest=ResourceReviewHistoryServiceTest test` passed.

## Re-review Verdict

- BL-01 resolved: `V2400__resource_review_history_permissions.sql` now inserts `permission_code`, `permission_name`, `resource_type`, and `resource_path`.
- HI-01 resolved: `AdminLayout.tsx` uses `REVIEW_HISTORY_ROLES = ['ADMIN', 'OPERATOR']`, and `ResourceReviewHistoryPage.tsx` blocks FINANCE before querying.
- MD-01 resolved: the page now renders `开始时间` and `结束时间` `datetime-local` filters wired to `createdFrom` and `createdTo`.
- MD-02 resolved: date parsing now catches `DateTimeParseException` and raises `BusinessException` with review-history validation codes.
- LO-01 resolved: the unit and Playwright tests now run the OPERATOR path with mocked `review-history:menu` and `review-history:read` permissions.

## Second Re-review Verdict

- Direct detail lookup resolved: `ResourceReviewHistoryService.detail` now calls a type-specific `detailSql(type)` query with `WHERE h.id=?`, avoiding the previous full result-set scan.
- Page size bound mostly resolved: `pageSize` defaults to 50 and rejects values below 1 or above 200.
- No blocker or high regressions were found in the staged Claude HIGH fix.
- One medium pagination issue remained open at this checkpoint: very large accepted `page` values could overflow the computed offset.

## Third Re-review Verdict

- MD-03 resolved: `ResourceReviewHistoryService` now caps `page` at 10000 while `pageSize` remains capped at 200, so the maximum `safePage * safePageSize` offset is 2,000,000 and cannot overflow `int`.
- Regression test added: `ResourceReviewHistoryServiceTest` verifies `page=10001&pageSize=200` is rejected with `REVIEW_HISTORY_PAGE_INVALID`.
- No blocker, high, medium, or low findings remain unresolved.

## Blockers

### BL-01: Flyway migration uses non-existent permission columns

**File:** `core/src/main/resources/db/migration/V2400__resource_review_history_permissions.sql:1`

**Issue:** The migration inserts into `permissions(code, name, type, resource, ...)`, but the actual table is defined as `permission_code`, `permission_name`, `resource_type`, and `resource_path` in `V1__init_schema.sql:53-64`, and all existing permission migrations use those names. On a real schema this migration fails before Phase 15 can deploy, so the review-history endpoint permissions are never registered.

**Fix:**

```sql
INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('review-history:menu', '统一审核历史菜单', 'MENU', '/admin/review-history', NULL, NULL, 350, 'ACTIVE'),
    ('review-history:read', '查看统一审核历史', 'API', '/api/v1/console/review-history', 'GET', NULL, 360, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    resource_type = VALUES(resource_type),
    resource_path = VALUES(resource_path),
    http_method = VALUES(http_method),
    sort_order = VALUES(sort_order),
    status = VALUES(status);
```

## High

### HI-01: Frontend can expose the route to FINANCE users that the backend always rejects

**File:** `web/src/components/layout/AdminLayout.tsx:13`

**Issue:** `OPERATIONS` includes `FINANCE`, and the new nav item inherits that role set at `AdminLayout.tsx:23`. `ResourceReviewHistoryPage.tsx:36` also enables the query for any platform role, including `FINANCE`, when `review-history:read` is present. The backend class guard at `ResourceReviewHistoryController.java:17` only allows `ADMIN` and `OPERATOR`, so a FINANCE user granted the visible permission can see the route and then receive 403s from both list and detail APIs. This violates the permission contract by splitting authorization differently across nav/page/API.

**Fix:** Align all three layers to one policy. If the intended contract is ADMIN/OPERATOR only, define a Phase 15 role set without FINANCE and use it for the nav/page gate:

```tsx
const REVIEW_HISTORY_ROLES: PlatformUserType[] = ['ADMIN', 'OPERATOR'];
// ...
{ to: '/admin/review-history', label: '审核历史', roles: REVIEW_HISTORY_ROLES, permissions: [REVIEW_HISTORY_PERMISSIONS.menu, REVIEW_HISTORY_PERMISSIONS.read] }
```

Also keep `ResourceReviewHistoryPage` from querying unless `userType === 'ADMIN' || userType === 'OPERATOR'`, and add an operator/finance authorization test.

## Medium

### MD-01: Decision-time filters are in the API contract but not rendered in the page

**File:** `web/src/pages/admin/review/ResourceReviewHistoryPage.tsx:62`

**Issue:** The Phase 15 spec requires filtering by decision time, and `ResourceReviewHistoryFilters` includes `createdFrom` and `createdTo`, but the production filter panel only renders resource type, tenant, state, actor, risk, and keyword controls. Operators cannot set the decision-time filters from `/admin/review-history`, so a required search dimension is missing.

**Fix:** Add two stable, labelled `datetime-local` inputs wired to `filters.createdFrom` and `filters.createdTo`, and extend the unit and Playwright assertions to verify both query parameters are emitted.

```tsx
<label>开始时间
  <input
    type="datetime-local"
    value={filters.createdFrom}
    onChange={(event) => setFilters({ ...filters, createdFrom: event.target.value })}
  />
</label>
<label>结束时间
  <input
    type="datetime-local"
    value={filters.createdTo}
    onChange={(event) => setFilters({ ...filters, createdTo: event.target.value })}
  />
</label>
```

### MD-02: Invalid date query parameters bypass business validation and return 500s

**File:** `core/src/main/java/com/ycsopen/sms/core/service/review/ResourceReviewHistoryService.java:137`

**Issue:** `createdFrom` and `createdTo` are parsed with `LocalDateTime.parse(...)` and only `NumberFormatException` is handled elsewhere. A malformed query such as `createdFrom=not-a-date` throws `DateTimeParseException`, which is not converted to a `BusinessException`, so the read-only search endpoint returns an internal error instead of a controlled validation response.

**Fix:** Parse both date filters through a helper that catches `DateTimeParseException` and raises the same domain validation style as the other filters.

```java
private static Timestamp timestampValue(String raw, String errorCode) {
    try {
        return Timestamp.valueOf(LocalDateTime.parse(raw.trim()));
    } catch (DateTimeParseException ex) {
        throw failure(errorCode, "筛选条件不合法");
    }
}
```

Use that helper at both date-filter call sites and add a service/controller test for invalid dates.

### MD-03: Large accepted page values can overflow OFFSET

**File:** `core/src/main/java/com/ycsopen/sms/core/service/review/ResourceReviewHistoryService.java:84`

**Issue:** The staged fix bounds `pageSize`, but `page` remains any non-negative `int` and the offset is computed as `safePage * safePageSize` using `int` arithmetic. A request such as `page=2147483647&pageSize=200` overflows before the value is bound to JDBC, producing a negative or wrapped `OFFSET` and turning an invalid pagination request into database-dependent incorrect behavior or a 500 response.

**Fix:** Compute the offset with `long`/`Math.multiplyExact` and either cap `page` or reject offsets beyond a documented maximum before appending the SQL parameter.

```java
private static long boundedOffset(int page, int pageSize) {
    try {
        long offset = Math.multiplyExact((long) page, (long) pageSize);
        if (offset > Integer.MAX_VALUE) {
            throw failure("REVIEW_HISTORY_PAGE_INVALID", "分页参数不合法");
        }
        return offset;
    } catch (ArithmeticException ex) {
        throw failure("REVIEW_HISTORY_PAGE_INVALID", "分页参数不合法");
    }
}
```

Add a service test for an oversized page value.

## Low

### LO-01: Tests claim the operator path but only exercise ADMIN

**File:** `web/test/scripts/resource-review-history.spec.ts:50`

**Issue:** The test matrix says "Operator searches unified signature/template/exemption history", but the Playwright setup stores `userType: 'ADMIN'`. The unit test also seeds `ADMIN` at `web/test/unit/resource-review-history.test.tsx:68`. Because ADMIN bypasses `review-history:read` checks in the UI, these tests do not prove the required authorized-operator permission path and do not catch the FINANCE/API mismatch above.

**Fix:** Add an OPERATOR test that mocks `/console/account-overview` with `review-history:read` and `review-history:menu`, confirms the nav/page/table/detail path works, and add a negative test for FINANCE or a user without the permission. Keep the ADMIN smoke test only as a separate bypass case if needed.

---

Final unresolved counts: BLOCKER 0, HIGH 0, MEDIUM 0, LOW 0, TOTAL 0.

_Reviewed: 2026-09-09T03:21:38Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
