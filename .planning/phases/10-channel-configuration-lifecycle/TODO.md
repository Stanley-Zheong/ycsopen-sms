# Authoritative Phase TODO

Every item starts open. Check it only after the named executable evidence is
present and the scoped TODO query confirms the obligation is closed.

## Entry gate

- [x] Entry review, spec, design, schema claims, and four runnable plans pass — Evidence: `ENTRY-REVIEW.md` and entry validator

## Implementation obligations

- [x] OBL-F-4-1-A complete channel configuration — Evidence: `EVIDENCE/OBL-F-4-1-A.json`
- [x] OBL-F-4-1-B protocol validation and connectivity conformance — Evidence: `EVIDENCE/OBL-F-4-1-B.json`
- [x] OBL-F-4-1-C stable/versioned channel pricing — Evidence: `EVIDENCE/OBL-F-4-1-C.json`
- [x] OBL-F-4-2-A no-restart effective version hot-load — Evidence: `EVIDENCE/OBL-F-4-2-A.json`
- [x] OBL-F-4-2-B failed hot-load retains prior version with audit/retry — Evidence: `EVIDENCE/OBL-F-4-2-B.json`
- [x] OBL-F-4-4-A dependency inventory before retirement — Evidence: `EVIDENCE/OBL-F-4-4-A.json`
- [x] OBL-F-4-4-B explicit dependency migration before offline/delete — Evidence: `EVIDENCE/OBL-F-4-4-B.json`
- [x] OBL-FIELD-CHANNEL-NAME required unique maximum 50 — Evidence: `EVIDENCE/OBL-FIELD-CHANNEL-NAME.json`
- [x] OBL-FIELD-CHANNEL-PROTOCOL allowed protocol enum — Evidence: `EVIDENCE/OBL-FIELD-CHANNEL-PROTOCOL.json`
- [x] OBL-FIELD-CHANNEL-ENDPOINT host/port and conformance — Evidence: `EVIDENCE/OBL-FIELD-CHANNEL-ENDPOINT.json`
- [x] OBL-FIELD-CHANNEL-CREDENTIAL protected masked credential — Evidence: `EVIDENCE/OBL-FIELD-CHANNEL-CREDENTIAL.json`
- [x] OBL-FIELD-CHANNEL-CONNECTION positive connection/window values — Evidence: `EVIDENCE/OBL-FIELD-CHANNEL-CONNECTION.json`
- [x] OBL-FIELD-CHANNEL-PRICE non-negative four-decimal price — Evidence: `EVIDENCE/OBL-FIELD-CHANNEL-PRICE.json`
- [x] OBL-FIELD-CHANNEL-PRIORITY optional 1-100 priority default 50 — Evidence: `EVIDENCE/OBL-FIELD-CHANNEL-PRIORITY.json`
- [x] OBL-STATE-CHANNEL-OFFLINE dependency-gated offline transition — Evidence: `EVIDENCE/OBL-STATE-CHANNEL-OFFLINE.json`
- [x] OBL-DATA-10-4-CHANNEL complete persisted channel facts — Evidence: `EVIDENCE/OBL-DATA-10-4-CHANNEL.json`

## Verification and delivery

- [x] Backend focused/full tests and MySQL evidence pass — Evidence: `10-VERIFICATION.md`
- [x] Frontend tests/build, production UI validator, and real Chrome evidence pass — Evidence: `EVIDENCE/ui-contract.json`, `EVIDENCE/playwright-execution.json`
- [x] Independent review has no unresolved BLOCKER/HIGH — Evidence: `10-REVIEW.md`
- [x] Claude review has no unresolved BLOCKER/HIGH — Evidence: `CLAUDE-REVIEW.md`
- [x] Atomic commit is visible on the configured GitHub remote — Evidence: `SUMMARY.md`

The scoped TODO set is empty when this document is included in the Phase10
delivery commit.
