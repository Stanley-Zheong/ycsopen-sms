# Spirit 02 Iterations

| Iteration ID | Trigger or finding | Evidence | Change made | Affected behavior/decision | Recheck |
|---|---|---|---|---|---|
| FE02-I-001 | Open issue `#92` and prior issues `#90`/`#91` | GitHub issue list, `docs/ISSUE_BUG_RETROSPECTIVE.md` | Defined verb-first operations action scope | FE-SPIRIT-02-ALERT-ACTIONS, FE-SPIRIT-02-ROW-STATUS | Pending implementation |
| FE02-I-002 | Issue `#66` closure audit found that an `APPLIED` remediation enabled recovery and failed remediation state disappeared after refresh | Phase 41 API/page/test inspection | Bind recovery eligibility to persisted `FAILED` remediation records | FE-SPIRIT-02-COMPLAINTS; DR-FE02-002 | Backend 12/12 and frontend 10/10 targeted tests pass; Chrome PR check pending |
| FE02-I-003 | F-9.4 requires complaint trend, while the analytics response exposed only totals and distributions | `docs/PRD.md` F-9.4 and current analytics response | Add ordered daily trend to the API and analytics page | FE-SPIRIT-02-COMPLAINTS; DR-FE02-003 | Backend 12/12 and frontend 10/10 targeted tests pass; Chrome PR check pending |
| FE02-I-004 | Independent review found a global remediation-record cutoff and misleading analytics zero cards during loading/error | Pre-push review against the full local diff | Scope remediation readback to the visible complaint set and render analytics cards only after successful data load | DR-FE02-002; DR-FE02-003 | Incremental independent re-review: no remaining BLOCKER, HIGH, or MEDIUM finding |
