---
phase: 08-tenant-qualification-status
depth: final
findings:
  blocker: 0
  high: 0
  medium: 0
  low: 0
  total: 0
status: clean
verdict: PASS
---

# Phase 08 Independent Review

Implementation and executable evidence are assembled. The parent workflow must still complete the independent final review and Claude review before closing the delivery TODO and committing the phase.

## Independent adversarial review

**Scope:** read-only review of the complete Phase 08 implementation, its
verification ledger, all 21 obligation records, real-service Chrome evidence,
and the current source diff. The protected Phase 02 PNG was not inspected or
modified. No full suite was rerun.

### Critical findings

#### CR-01 — Legacy console registration bypasses the Phase 08 qualification boundary

**File:** `core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java:29-39`, `core/src/main/java/com/ycsopen/sms/core/service/tenant/TenantService.java:37-60`

**Issue:** The still-documented `POST /api/v1/console/tenants/register` endpoint
continues to call `TenantService.submitRegistration`, which only invokes the
protected-object adapter. It does not invoke `TenantQualificationValidator`,
does not consume a verified contact challenge, does not create the initial
administrator, and does not populate the Phase 08 qualification fields before
returning a tenant in `PENDING`. The adapter checks only shape (for example,
18 characters), so this second registration path can create records that the
new public/self-service path would reject. Such records later cannot be
approved by `TenantReviewService` because `initialAdminUserId` is absent.

**Fix:** Remove the legacy registration route and update its callers/docs, or
make it delegate to one authoritative registration service with the same
field/checksum/contact-challenge/credential contract and an explicitly
authenticated operator policy. Add an MVC test proving malformed and
unverified submissions cannot create a tenant through either route.

#### CR-02 — Existing approval/rejection API contract was removed without a compatibility path

**File:** `core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java:41-73`, `web/src/api/tenants.ts:4-15`, `core/docs/API.md:32-34`

**Issue:** Phase 08 removes the controller mappings for
`/api/v1/console/tenants/{id}/approve-and-activate-trial` and
`/api/v1/console/tenants/{id}/reject`, while the repository API documentation
and the existing frontend client still call the approval endpoint. Those
clients now receive 404s. The replacement admin endpoints use a different
request shape and require inspection/human confirmation, but no migration,
redirect, or versioned deprecation is provided. This is a breaking API change
outside the documented Phase 08 route addition.

**Fix:** Preserve the old mappings as compatibility adapters that translate to
the new review service (and reject unsafe legacy requests with a stable error),
or remove/update every caller and API document in the same change after an
explicit versioned migration. Add controller contract tests for the chosen
compatibility behavior.

### Important findings

#### HI-01 — Eligibility check is not linearizable with account disable/freeze

**File:** `core/src/main/java/com/ycsopen/sms/core/service/tenant/TenantEligibilityPolicy.java:28-42`, `core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java:60-63`

**Issue:** `requireNewWorkAllowed` performs ordinary snapshot reads of the
tenant and account. It does not lock either row or recheck them in the same
transaction immediately before message persistence. A concurrent status change
can commit after these reads and before `MessageSubmitService` creates the
message/billing records, allowing a new submission after the operator has
disabled or arrears-frozen the account. The real-MySQL evidence tests concurrent
status mutations, but not this submission-vs-status race.

**Fix:** Use the existing `findByIdForUpdate` and
`findByTenantIdForUpdate` in a stable lock order from the outer submit
transaction, or introduce one narrowly scoped transactional fence that locks
both rows and performs the eligibility check immediately before downstream
work. Add a physical MySQL test racing submit against status change and assert
that the loser cannot persist new work.

### Medium findings

#### MD-01 — Reviewer UI cannot inspect the required legal-identity evidence

**File:** `web/src/pages/admin/tenants/TenantListPage.tsx:135-147,315`, `web/src/api/tenantQualificationApi.ts:293-298`

**Issue:** The backend exposes five evidence kinds, but the production admin UI
offers only a “查看营业执照证明” action and the client hard-codes
`BUSINESS_LICENSE`. F-2.2 requires checking the business license and both legal
representative identity images before an approval; the UI therefore cannot
perform the review it claims to complete. The human-confirmation checkbox can
be checked without any UI path to the two identity documents.

**Fix:** Add explicit front/back identity evidence actions (with the same
one-time application-mediated capability and safe error states), or narrow the
approval contract and PRD trace to a documented non-UI review boundary. Extend
the Chrome scenario to open both documents before approval.

#### MD-02 — Finance users are excluded from the institution-management navigation

**File:** `web/src/components/layout/AdminLayout.tsx:11-15,47-48`

**Issue:** The PRD labels F-2 as shared by operations and finance, and the
backend accepts `FINANCE` users with the Phase 08 permissions. The UI nav item
is restricted to `OPERATIONS = ['ADMIN', 'OPERATOR']`, so a permitted finance
user cannot reach `/admin/tenants` through the product navigation. Direct URL
access works only accidentally and is not a usable workflow.

**Fix:** Include `FINANCE` in the role/navigation policy, while retaining the
existing per-action permission checks so finance users see only authorized
review/edit/status controls.

## Evidence consistency review

- The 21 obligation records are present and marked `PASS`; the normalized
  Playwright record reports 18 expected blocks, all passed, with local Chrome
  152.0.7977.76, one worker, and 1440x900 asserted.
- The production UI validator reports 3 routes, 105 selectors, 15 owned
  elements, and 3 pages. The Playwright source contains no project API route
  interception or browser download.
- Backend, frontend, real-MySQL, provider, protected-object, and UI-contract
  evidence is internally consistent for the exercised paths. That evidence
  does not cover the legacy registration/approval API paths, submit-vs-status
  concurrency race, legal-identity reviewer actions, or finance navigation
  path above.

## Verdict

**BLOCKED** — two Critical, one Important, and two Medium findings remain.
The phase TODO must not be closed and no Phase 08 commit should be created
until these findings are either fixed with executable evidence or explicitly
removed from scope through a documented API/requirements decision.

_Reviewer: independent Phase 08 adversarial reviewer_

## Re-review after implementation fixes

**Reviewed:** 2026-09-08

The five findings above were re-checked against the current worktree. The
functional changes resolve the original defects:

| Original finding | Re-review result | Current evidence |
| --- | --- | --- |
| CR-01 legacy registration bypass | Resolved | `TenantController.register` now rejects the legacy route before calling `TenantService`; `TenantControllerCompatibilityTest` asserts 410 and no service interaction. |
| CR-02 legacy decision contract | Resolved | The old approve/reject mappings remain as explicit 410 migration responses; `core/docs/API.md` documents the migration and compatibility tests assert both codes. |
| HI-01 eligibility/status race | Resolved in implementation | `TenantEligibilityPolicy.requireNewWorkAllowed` now runs transactionally and locks tenant then account with `findByIdForUpdate`/`findByTenantIdForUpdate` before downstream message work. |
| MD-01 legal-identity evidence unavailable | Resolved in implementation | `TenantListPage` now requests `LEGAL_REP_ID_FRONT` and `LEGAL_REP_ID_BACK` and renders separate front/back controls. |
| MD-02 finance navigation | Resolved | `AdminLayout.OPERATIONS` now includes `FINANCE` and retains the tenant menu/read permission checks. |

### Remaining medium findings

#### MD-03 — Production UI contract is stale after the evidence-control change

**File:** `web/src/pages/admin/tenants/TenantListPage.tsx:317`,
`.planning/phases/08-tenant-qualification-status/UI-ELEMENTS.md:89`,
`.planning/phases/08-tenant-qualification-status/EVIDENCE/ui-contract.json`

**Issue:** The implementation adds the two selectors
`admin-tenant-qualification-tenants-review-evidence-legal-id-front-open` and
`admin-tenant-qualification-tenants-review-evidence-legal-id-back-open`, but
neither selector is in the documented UI element inventory or the 105-selector
contract manifest. The recorded source hashes also no longer match the changed
`AdminLayout.tsx` and `TenantListPage.tsx`: running the required production
validator returns `ui_contract=BLOCKED` with two
`UI_SOURCE_CHECKSUM_MISMATCH` errors. The phase therefore cannot claim a
passing production UI contract.

**Fix:** Add both controls, their actions/states, and their Playwright IDs to
`UI-ELEMENTS.md`, `TEST-MATRIX.md`, `tenantQualificationTestIds.ts`, and every
contract/evidence inventory that intentionally owns the selectors. Recompute
the implementation hashes and rerun
`ruby .planning/tools/validate-ui-contract.rb --phase 08 --package tenant-qualification-status --stage production`.

#### MD-04 — Chrome evidence does not exercise the newly added identity controls

**File:** `web/test/scripts/tenant-qualification.spec.ts:249-252`

**Issue:** The current real-Chrome review scenario opens only the business
license selector. It does not click either new legal-representative ID control,
assert the corresponding front/back evidence dialog, or verify the safe error
path. Thus the implementation fix for the original reviewer-workspace gap has
no executable production-browser evidence, and the existing PASS record still
describes only the old action.

**Fix:** Extend the real-Chrome review scenario to open both new selectors,
assert the front/back dialog titles and close behavior (including a failed
fetch if that boundary is in scope), then refresh the raw report and the
per-obligation evidence record.

#### MD-05 — Null JSON on the retained legacy registration route causes a 500

**File:** `core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java:42-49`

**Issue:** A syntactically valid JSON body of `null` can be bound as a null
`TenantRegistrationRequest`. The compatibility handler then dereferences it
at `request.hasLegacyObjectUrlInput()` before returning its intended 410
migration response. This leaves a malformed legacy request as an unhandled
server error rather than a stable safe rejection.

**Fix:** Make the guard null-safe (`request != null && ...`) or, because this
route never creates tenants, return the 410 migration response before reading
the body. Add an MVC test posting the literal JSON `null` and assert the stable
error contract.

### Minor observation

#### IN-01 — Legacy approval adapter contains unreachable duplicate logic

**File:** `core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java:59-64`

The condition and the unconditional throw have the same outcome, and
`tenantReviews`, `tenantId`, and the legacy request parameters are unused.
Remove the dead conditional or make the adapter either validate and translate
the legacy request or immediately return the single migration response. This
does not change the current security result, but it obscures the compatibility
contract.

## Re-review verdict

**BLOCKED** — all five original functional findings are resolved, but the
production UI contract validator is currently blocked and the new evidence
actions lack real-Chrome coverage. No Critical/Blocker remains; MD-03 and
MD-04 must be closed before the Phase 08 TODO and final commit can be marked
complete.

_Re-reviewer: independent Phase 08 adversarial reviewer_

## Final re-review after the second fix pass

**Reviewed:** 2026-09-08

The second fix pass was checked read-only. The null-registration guard is now
null-safe, the duplicate legacy approval branch and unused conditional are
gone, the browser source now contains front/back evidence assertions, and all
eight recorded implementation hashes match their current files. The two
planning validators also pass:

```text
ui_contract=PASS phase=08 package=tenant-qualification-status stage=production mode=production selectors=105 routes=3 owned_elements=15 owned_pages=3
validation=PASS count=522 fields=9 requirements=108/108 unknown_requirements=0 duplicate_record_requirement_links=0 owners=56/56 unknown_owners=0 duplicate_obligation_ids=0 duplicate_test_ids=0 duplicate_evidence_targets=0 element_refs=195 invalid_element_refs=0 ui_owners=42 non_ui_owner_refs=0 selected=21 projects=19
```

### Remaining medium findings

#### MD-06 — Newly added evidence controls are still absent from the selector contract

**File:** `web/src/pages/admin/tenants/TenantListPage.tsx:317`,
`web/src/components/tenant/tenantQualificationTestIds.ts:119-120`,
`.planning/phases/08-tenant-qualification-status/UI-ELEMENTS.md:89`,
`.planning/phases/08-tenant-qualification-status/EVIDENCE/ui-contract.json`

**Issue:** The source renders
`admin-tenant-qualification-tenants-review-evidence-legal-id-front-open` and
`admin-tenant-qualification-tenants-review-evidence-legal-id-back-open`, but
the reviewed selector catalog, UI-ELEMENTS inventory, and all 105-selector
contract lists still contain only the business-license action. The production
validator passes because it verifies that the declared 105 selectors exist;
it does not reject undeclared selectors. This leaves the required “every UI
element/action has a documented test-id” contract incomplete even though the
source hashes now agree.

**Fix:** Add both selectors as explicit UI-ELEMENTS rows with action, states,
permission, test case, and evidence references; add them to
`tenantQualificationTestIds.ts`, `TEST-MATRIX.md`, and each intended contract
inventory, then rerun the validator with the resulting selector count.

#### MD-07 — Updated Chrome assertions have not produced refreshed execution evidence

**File:** `web/test/scripts/tenant-qualification.spec.ts:253-260`,
`.planning/phases/08-tenant-qualification-status/EVIDENCE/phase08-playwright-raw.json`

**Issue:** The Playwright source now clicks and asserts both identity evidence
dialogs, but the recorded raw report is still dated
`2026-09-07T15:26:22.956Z`, before the source change (the source file was
updated on 2026-09-08). The PASS evidence therefore cannot prove that the new
front/back assertions ran in real Chrome; the normalized execution record and
per-obligation review evidence remain from the older scenario.

**Fix:** Run the focused real-Chrome Playwright command after the source and
contract updates, replace the raw report and its hash/metadata, and link the
new run from `OBL-F-2-2-A.json` and `playwright-execution.json`.

## Final re-review verdict

**BLOCKED** — no Critical or High finding remains, and the original five
functional findings plus the null-input and duplicate-branch issues are
resolved. MD-06 and MD-07 remain Medium acceptance/documentation gaps. Keep
the Phase 08 TODO and final commit open until both are closed with refreshed
contract and browser evidence.

_Final re-reviewer: independent Phase 08 adversarial reviewer_

## Closing re-review

**Reviewed:** 2026-09-08

The final evidence refresh closes MD-06 and MD-07. The selector catalog,
UI-ELEMENTS inventory, TEST-MATRIX, and all contract inventories now contain
107 selectors, including the legal-representative ID front/back actions. The
required production UI and PRD validators both pass. The refreshed real-Chrome
report records all 18/18 cases passing, including the front and back evidence
dialogs at `web/test/scripts/tenant-qualification.spec.ts:253-260`; its run
started at `2026-09-08T00:59:15.644Z`, after those assertions were added.

The complete current-worktree review has no unresolved Critical, High, or
Medium findings. The original five findings and the later null-input,
documentation, evidence, and dead-branch observations are closed. No source
file or protected PNG was modified by this review.

## Closing verdict

**PASS** — Phase 08 review is clear. The phase may proceed to its final
TODO/commit workflow, subject to the parent workflow recording the final
commit and keeping the evidence provenance aligned with that commit.

_Closing reviewer: independent Phase 08 adversarial reviewer_
