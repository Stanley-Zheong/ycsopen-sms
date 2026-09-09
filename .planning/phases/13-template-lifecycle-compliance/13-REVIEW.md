---
phase: 13-template-lifecycle-compliance
reviewed: 2026-09-09T01:36:11Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - core/src/main/java/com/ycsopen/sms/core/config/SecurityConfig.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/Template.java
  - core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java
  - core/src/main/java/com/ycsopen/sms/core/service/template/TemplateLifecycleService.java
  - core/src/main/java/com/ycsopen/sms/core/service/template/TemplateRuleEngine.java
  - core/src/main/java/com/ycsopen/sms/core/service/template/TemplateSendComplianceService.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/TemplateLifecycleController.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TemplateApplicationRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TemplateDecisionRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TemplatePreviewRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TemplatePreviewResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TemplateResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TemplateReviewQueueResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TemplateReviewSummaryResponse.java
  - core/src/main/resources/db/migration/V2200__template_lifecycle_compliance.sql
  - core/src/test/java/com/ycsopen/sms/core/service/message/MessageSubmitServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/template/TemplateLifecycleServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/template/TemplateSendComplianceServiceTest.java
  - web/src/api/templateLifecycleApi.ts
  - web/src/components/layout/AdminLayout.tsx
  - web/src/components/layout/TenantLayout.tsx
  - web/src/pages/admin/templates/TemplateReviewPage.tsx
  - web/src/pages/tenant/templates/TemplateLifecyclePage.tsx
  - web/src/router/routes.tsx
  - web/src/styles/template-lifecycle.css
  - web/test/scripts/template-lifecycle.spec.ts
  - web/test/unit/template-lifecycle.test.tsx
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
---

# Phase 13: Code Review Report

**Reviewed:** 2026-09-09T01:36:11Z
**Depth:** standard
**Files Reviewed:** 27
**Status:** clean

## Summary

Reviewed Phase 13 template lifecycle compliance changes only: backend lifecycle/send validator, migration, route/layout integration, tenant/admin UI, and related tests. Initial findings are retained for history; the latest re-review below is authoritative and shows no unresolved BLOCKER/HIGH findings.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Resubmitting an old rejected template can create duplicate or parallel successor versions

**Classification:** BLOCKER
**File:** `core/src/main/java/com/ycsopen/sms/core/service/template/TemplateLifecycleService.java:34`
**Issue:** `resubmit()` only checks that the selected previous row is `REJECTED` or `AMENDMENT_REQUIRED`, then creates `previous.versionNo() + 1`. The original rejected row remains rejected after the first resubmission, so the same `previousTemplateId` can be resubmitted again. With identical payloads this regenerates the same deterministic template code at `templateCode()` (`versionNo` is still `2`) and trips the `(tenant_id, template_code)` unique key as an unhandled persistence failure. With changed content/rules it creates another version-2 child from the same predecessor, splitting lifecycle history and leaving multiple pending successors for one rejected/amendment row. That breaks the Phase 13 state/version/history contract and can surface as a 500 after the first successful resubmit.
**Fix:**
```java
private void requireNoSuccessor(long previousTemplateId) {
    Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM templates WHERE previous_template_id=?",
            Integer.class,
            previousTemplateId);
    if (count != null && count > 0) {
        throw failure("TEMPLATE_RESUBMIT_SUCCESSOR_EXISTS", "当前模板已有重新提交版本");
    }
}

@Transactional
public TemplateResponse resubmit(long tenantId, long previousTemplateId, TemplateApplicationRequest request) {
    TemplateRow previous = row(previousTemplateId);
    if (previous.tenantId() != tenantId) {
        throw failure("TEMPLATE_FORBIDDEN", "不能修改其他机构模板");
    }
    if (!Set.of("REJECTED", "AMENDMENT_REQUIRED").contains(previous.auditStatus())) {
        throw failure("TEMPLATE_RESUBMIT_STATE_INVALID", "当前模板状态不可重新提交");
    }
    requireNoSuccessor(previousTemplateId);
    return createVersion(tenantId, previousTemplateId, previous.versionNo() + 1, request);
}
```
Also add a service test that resubmitting the same rejected/amendment template twice fails with the business error and does not insert a second child.

## Warnings

### WR-01: Admin review decisions save successfully but the visible queue remains stale

**Classification:** WARNING (HIGH)
**File:** `web/src/pages/admin/templates/TemplateReviewPage.tsx:31`
**Issue:** The mutation success handler closes the modal and shows "saved", but it does not invalidate/refetch `['template-review', keyword]` or update the cached row with the returned `TemplateRecord`. The current table and summary continue showing the old `PENDING` state after approval/rejection/amendment until a reload or unrelated refetch. This invalidates the Phase 13 requirement that review decisions are visible, and the current tests only assert the POST payload/status toast, not the resulting row state.
**Fix:** Use `useQueryClient()` and invalidate the active review query on success, or update `queue` data from the mutation result.
```tsx
const queryClient = useQueryClient();

const mutation = useMutation({
  mutationFn: /* existing mutation */,
  onSuccess: async () => {
    setDecisionTarget(null);
    setDecision('APPROVE');
    setOpinion('');
    setMessage('模板审核结果已保存。');
    setError('');
    await queryClient.invalidateQueries({ queryKey: ['template-review'] });
  },
});
```
Add a unit/Chrome assertion that the reviewed row no longer displays `PENDING` after the decision succeeds.

### WR-02: Tenant preview UI only supports a hard-coded `code` variable

**Classification:** WARNING (HIGH)
**File:** `web/src/pages/tenant/templates/TemplateLifecyclePage.tsx:122`
**Issue:** The backend extracts arbitrary variable names from approved template content, but the tenant preview panel always renders and sends only `variables.code`. A template containing `${amount}`, `${name}`, or multiple variables cannot be previewed from the UI: the API receives an extra `code` parameter and misses the actual variable names, causing `TEMPLATE_VARIABLE_MISSING`/`TEMPLATE_VARIABLE_EXTRA`. This invalidates Phase 13's variable preview workflow for anything except the test fixture's `${code}` case.
**Fix:** Render preview inputs from the selected template's `variableNames` and send exactly those keys.
```tsx
const target = previewTarget ?? templates.data?.[0] ?? null;
const previewVariables = target?.variableNames ?? [];

{previewVariables.map((name) => (
  <label key={name}>{name}
    <input
      value={variables[name] ?? ''}
      onChange={(event) => setVariables((current) => ({ ...current, [name]: event.target.value }))}
    />
  </label>
))}
```
Add unit/Chrome coverage with a non-`code` variable and a multi-variable template.

## Commands Inspected

- `sed -n '1,220p' AGENTS.md`
- `sed -n '1,260p' /Users/laosanzheong/.agents/skills/gsd-code-review/SKILL.md`
- `git status --short --branch`
- `git merge-base HEAD main`
- `git diff --name-only c377e4fc30dc0596e646cece1955b030d3cbe7b1..HEAD -- . ':!.planning/' ...`
- `rg -n "CREATE TABLE templates|ALTER TABLE templates|template_review_history|audit_status|signature_id|param_check_rule|is_system_template" core/src/main/resources/db/migration core/src/test -g'*.sql' -g'*.java'`
- `rg -n "password|secret|api_key|token\\s*[=:]|eval\\(|innerHTML|dangerouslySetInnerHTML|exec\\(|console\\.log|debugger;|TODO|FIXME|XXX|HACK|catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}" ...`
- `mvn -q -f core/pom.xml -Dtest=TemplateLifecycleServiceTest,TemplateSendComplianceServiceTest,MessageSubmitServiceTest test` (PASS)

## Suggested Verification After Fixes

- `mvn -q -f core/pom.xml -Dtest=TemplateLifecycleServiceTest,TemplateSendComplianceServiceTest,MessageSubmitServiceTest test`
- `mvn -q -f core/pom.xml test`
- `npm --prefix web test -- template-lifecycle.test.tsx`
- `npm --prefix web test`
- `npm --prefix web run build`
- `npm exec -- playwright test template-lifecycle.spec.ts --project=local-google-chrome --reporter=json`

## Re-review

**Re-reviewed:** 2026-09-09T01:36:11Z
**Final unresolved BLOCKER/HIGH status:** 0 BLOCKER, 0 HIGH unresolved.

- CR-01 is resolved. `TemplateLifecycleService.resubmit()` now calls `requireNoSuccessor()` before creating a child version, `V2200__template_lifecycle_compliance.sql` adds `uk_templates_previous_template`, and `TemplateLifecycleServiceTest` covers duplicate resubmit rejection with `TEMPLATE_RESUBMIT_SUCCESSOR_EXISTS`.
- WR-01 is resolved. `TemplateReviewPage` now invalidates `['template-review']` after decision save, and unit/Chrome tests assert the row updates to `AMENDMENT_REQUIRED`.
- WR-02 is resolved. `TemplateLifecyclePage` now builds `scopedVariables` from `target.variableNames` before calling `previewTemplate()`, so stale values from a previously selected template are not posted. `template-lifecycle.test.tsx` asserts switching from a `${name}`/`${amount}` template to a `${code}` template posts exactly `{ variables: { code: '2468' } }`.
- No new BLOCKER/HIGH findings were found in the WR-02 re-review scope.

Commands run during re-review:

- `mvn -q -f core/pom.xml -Dtest=TemplateLifecycleServiceTest,TemplateSendComplianceServiceTest,MessageSubmitServiceTest test` (PASS)
- `npm --prefix web test -- template-lifecycle.test.tsx` (PASS)
- `npm exec -- playwright test template-lifecycle.spec.ts --project=local-google-chrome --reporter=json` (PASS, 4 expected, 0 unexpected, 0 flaky)
- `npm --prefix web test -- template-lifecycle.test.tsx` (latest WR-02 re-review, PASS)

## Resolution

- CR-01 fixed by adding a business check for existing successors and a database unique constraint on `previous_template_id`; `TemplateLifecycleServiceTest` now asserts a second resubmission is rejected with `TEMPLATE_RESUBMIT_SUCCESSOR_EXISTS`.
- WR-01 fixed by invalidating the active `template-review` query after decision save; unit and Chrome tests now assert the row state updates to `AMENDMENT_REQUIRED`.
- WR-02 fixed by rendering preview inputs from the selected template's `variableNames` and scoping the preview POST payload to those names only; unit and Chrome tests now use a non-`code` multi-variable template, and unit coverage asserts a subsequent `code` preview posts only `{code}`.

All reviewed BLOCKER/HIGH findings are resolved.

---

_Reviewed: 2026-09-09T01:36:11Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
