# Authoritative Phase TODO

Check items only after named executable evidence exists.

## Entry

- [x] Scope, dependencies, and lean implementation decisions are recorded — Evidence: `12-SPEC.md`, `12-CONTEXT.md`, `DESIGN.md`, `DECISIONS.md`

## Implementation

- [x] Tenant application records required signature fields/proof and enters pending review — Evidence: `EVIDENCE/OBL-F-3-1-A.json`, `EVIDENCE/OBL-F-3-1-B.json`
- [x] Operator review stats/filter/detail expose real signature data — Evidence: `EVIDENCE/OBL-F-3-2-A.json`, `EVIDENCE/OBL-F-3-2-B.json`
- [x] Review decisions approve/reject/supplement with actor, opinion, time, risk context, and tenant-visible history — Evidence: `EVIDENCE/OBL-F-3-2-C.json`
- [x] Per-channel filing matrix records none/registering/registered/failed independently — Evidence: `EVIDENCE/OBL-F-3-3-A.json`
- [x] Filing request/result/retry are traceable and idempotent — Evidence: `EVIDENCE/OBL-F-3-3-B.json`
- [x] Usable-channel calculation requires approved signature, registered filing, and Phase 11 channel eligibility — Evidence: `EVIDENCE/OBL-F-3-3-C.json`
- [x] Tenant/admin UI pages implement documented selectors and actions — Evidence: `EVIDENCE/ui-contract.json`, `EVIDENCE/playwright-execution.json`

## Verification and delivery

- [x] Backend unit/full tests pass — Evidence: `12-VERIFICATION.md`
- [x] Frontend tests/build and Chrome acceptance pass — Evidence: `12-VERIFICATION.md`
- [x] Independent review has no unresolved BLOCKER/HIGH — Evidence: `12-REVIEW.md`
- [x] Claude review has no unresolved BLOCKER/HIGH — Evidence: `CLAUDE-REVIEW.md`
- [x] Atomic commit is visible on the configured GitHub remote — Evidence: `SUMMARY.md`
