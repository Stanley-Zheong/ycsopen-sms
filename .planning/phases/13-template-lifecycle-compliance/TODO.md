# Authoritative Phase TODO

Check items only after named executable evidence exists.

## Entry

- [x] Scope, dependencies, and lean implementation decisions are recorded — Evidence: `13-SPEC.md`, `13-CONTEXT.md`, `DESIGN.md`, `DECISIONS.md`

## Implementation

- [x] Tenant template application records name/content/type/signature/variables/rules/rationale — Evidence: `EVIDENCE/OBL-F-3-4-A.json`
- [x] Variable syntax, rules, preview, and injection rejection are enforced — Evidence: `EVIDENCE/OBL-F-3-4-B.json`
- [x] Admin review queue filters and displays template review data — Evidence: `EVIDENCE/OBL-F-3-5-A.json`
- [x] Review decisions are attributable, visible, and preserve submitted content — Evidence: `EVIDENCE/OBL-F-3-5-B.json`
- [x] Shared send validator covers domestic ingress contract — Evidence: `EVIDENCE/OBL-F-3-7-A.json`
- [x] Domestic free text and invalid template/signature/variable sends are rejected before task creation — Evidence: `EVIDENCE/OBL-F-3-7-B.json`
- [x] Template field and state-machine obligations are covered — Evidence: `EVIDENCE/OBL-FIELD-*.json`, `EVIDENCE/OBL-STATE-*.json`
- [x] Template data model preserves variables, binding, review, version, and history — Evidence: `EVIDENCE/OBL-DATA-10-3-TEMPLATE-EXEMPT.json`
- [x] Tenant/admin UI pages implement documented selectors and actions — Evidence: `EVIDENCE/ui-contract.json`, `EVIDENCE/playwright-execution.json`

## Verification and delivery

- [x] Backend unit/full tests pass — Evidence: `13-VERIFICATION.md`
- [x] Frontend tests/build and Chrome acceptance pass — Evidence: `13-VERIFICATION.md`
- [x] Independent review has no unresolved BLOCKER/HIGH — Evidence: `13-REVIEW.md`
- [x] Claude review has no unresolved BLOCKER/HIGH — Evidence: `CLAUDE-REVIEW.md`
- [x] Atomic commit is visible on the configured GitHub remote — Evidence: `SUMMARY.md`
