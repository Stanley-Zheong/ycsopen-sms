# Issue 120 Reviews

## Entry Review

| Criterion ID | Verdict | Evidence | Command or inspection rule |
|---|---|---|---|
| ENTRY-120-SCOPE | PASS | `SPEC.md` limits the change to Phase 32 admin uplink identity projection, filtering, API documentation, and `/admin/uplink`; replay, delivery state, persistence, tenant portal, and schema changes remain excluded. | Inspect `SPEC.md`, `DESIGN.md`, and Spirit 04 `SPEC.md`. |
| ENTRY-120-OWNERSHIP | PASS | `UplinkNormalizationService` owns joined read projections and bounded identity options; the controller, API adapter, React page, and API documentation retain distinct delivery responsibilities. | Inspect `DESIGN.md` ownership matrix and `DECISIONS.md`. |
| ENTRY-120-OPTION-SAFETY | PASS | The ADMIN/OPERATOR option contract returns only `tenantId`, institution number, short name, and full name; ambiguous text cannot select an arbitrary tenant, and result requests submit only `tenantId`. | Inspect `DR-120-002`, `UI-ELEMENTS.md`, and cases `C-120-UPLINK-FILTER`, `C-120-PUSH-FILTER`, and `C-120-TENANT-OPTIONS`. |
| ENTRY-120-FALLBACK | PASS | Left-joined nullable metadata preserves the stored tenant ID; list, detail, monitor, lookup-failure, and ambiguous-input states have explicit fallback and feedback contracts. | Inspect `DESIGN.md` state model and the identity/feedback rows in `UI-ELEMENTS.md`. |
| ENTRY-120-UI-TRACE | PASS | Every changed input, identity surface, option source, and feedback state has a stable selector with an exact route, behavior, Playwright ID, case ID, and executable command in the 11-column test matrix. | Cross-check `UI-ELEMENTS.md` against `TEST-MATRIX.md`. |
| ENTRY-120-VERIFICATION | PASS | Focused service and MockMvc API tests, React unit tests, installed Google Chrome Playwright, full backend/frontend/build checks, planning validators, and diff hygiene are declared; the Docker non-applicability boundary and replacement evidence are explicit. | Inspect Spirit 04 `QUALITY-GATEWAY.md`; run `git diff --check`. |

### Verdict

PASS. The issue contract is implementation-ready. This entry verdict does not replace implementation review, executed verification, or pre-push review.

## Implementation Review

| Criterion | Verdict | Evidence |
|---|---|---|
| Joined identity reads | PASS | List, detail, source lookup, and push monitor use one left-joined query each; push-monitor aggregation groups every projected tenant field and preserves rows whose tenant master data is absent. |
| Permission and compatibility | PASS | The identity-only option endpoint retains the existing ADMIN/OPERATOR boundary, leaks no protected tenant fields, and all list/detail/monitor response changes are additive while preserving `tenantId`. |
| Display and fallback | PASS | List and monitor show short name plus institution number, detail includes full name and internal ID, and every missing-metadata path retains an explicit stable-ID fallback. |
| Lookup round trip | PASS | Datalist values use unique `tenantNo` values while labels retain composite display text; the service searches number, short name, and full name, while backend-faithful browser/unit mocks verify display lookup resolves to requests containing only `tenantId`. |
| Query state machine | PASS | List and monitor validate independent option sets. Ambiguous input remains blocked on repeated submit; a lookup that changes from loading to resolved applies the complete current draft when it differs from the applied filter and refetches only when both are identical. |
| Regression evidence | PASS | The focused Vitest suite passes 7/7, including the loading-to-blocked-to-resolved retry, and `git diff --check` passes. Production build success was reported for the reviewed diff; browser execution remains part of the separate final verification gate. |

### Verdict

PASS. The three previously reported HIGH filter-state defects are resolved, and the final complete-diff review found no remaining BLOCKER or HIGH issue. This verdict does not replace the final verification and pre-push gates.

## Pre-Push Review

| Criterion | Verdict | Evidence |
|---|---|---|
| Scope and ownership | PASS | The final semantic diff remains limited to issue `#120`, its Phase 32 service/controller/API owners, `/admin/uplink`, focused tests, and Spirit 04 evidence; no persistence, replay, delivery-state, tenant-portal, or schema behavior changed. |
| Backend query semantics | PASS | Uplink list/detail/source reads and push monitor each project tenant identity in their owning SQL statement through `LEFT JOIN tenants`; missing master rows survive, monitor grouping includes every added projection, and the bounded option query returns only the four declared identity fields. |
| Permission and API compatibility | PASS | The new option route is restricted to ADMIN/OPERATOR, existing tenant-scoped reads retain their required tenant predicate, and list/detail/monitor changes are additive while preserving the stable `tenantId`. |
| Frontend behavior | PASS | List, detail, and monitor render the declared identity with stable-ID fallback. Independent list/monitor option queries round-trip `tenantNo`, ambiguous and unavailable lookups cannot submit display text, numeric fallback remains usable, and loading-to-valid retries apply the complete draft rather than stale filters. |
| Test contract | PASS | Focused Maven passes 7/7 and Vitest passes 7/7; Chrome cases trace the declared selectors and cover identity, missing metadata, stable-ID serialization, lookup failure, ambiguity, repeated submit, and loading-to-valid retry. |
| Evidence and hygiene | PASS | `VERIFICATION.md`, `TEST-MATRIX.md`, and Spirit 04 record the executed focused gates and disclose full-suite, validator, Docker, and browser-process environment boundaries without claiming those local boundaries as CI success. Final tracked and issue-owned untracked files have no whitespace errors; the repo precheck's stdout findings are unchanged baseline files outside this issue. |

### Verdict

PASS. The final semantic diff has no BLOCKER or HIGH finding and is ready for the branch commit and pull-request CI boundary. Merge remains conditional on the required pull-request checks.
