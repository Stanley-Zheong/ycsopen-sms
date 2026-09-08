# Authoritative Phase TODO

Every item starts open. Check it only after the named executable evidence is
present and the scoped TODO query confirms the obligation is closed.

## Entry gate

- [x] Entry review, spec, design, schema claims, and four runnable plans pass — Evidence: `ENTRY-REVIEW.md` and entry validator

## Implementation obligations

- [x] OBL-F-4-3-A heartbeat/test-message metrics are recorded and visible — Evidence: health-metrics JSON under `EVIDENCE/`
- [x] OBL-F-4-3-B sustained failure changes eligibility/state and emits one source event — Evidence: sustained-failure JSON under `EVIDENCE/`
- [x] OBL-F-4-6-A operators create weighted or primary-backup pools from eligible channels — Evidence: pool-create JSON under `EVIDENCE/`
- [x] OBL-F-4-6-B pool membership, primary, weights, disabled members, and concurrency validate deterministically — Evidence: pool-validation JSON under `EVIDENCE/`
- [x] OBL-F-4-7-A pause records trigger, actor/system, reason, and time — Evidence: pause-record JSON under `EVIDENCE/`
- [x] OBL-F-4-7-B paused channels are removed from new route candidates without in-flight migration claims — Evidence: candidate-fence JSON under `EVIDENCE/`
- [x] OBL-STATE-CHANNEL-PAUSE normal-to-paused transition exits route candidates — Evidence: channel-pause-state JSON under `EVIDENCE/`
- [x] OBL-STATE-CHANNEL-MAINTAIN planned maintenance makes the channel route-ineligible — Evidence: maintenance-start JSON under `EVIDENCE/`
- [x] OBL-STATE-CHANNEL-MAINTAIN-END maintenance ends only after successful health validation — Evidence: maintenance-end JSON under `EVIDENCE/`

## Verification and delivery

- [x] Backend focused/full tests and MySQL evidence pass — Evidence: `11-VERIFICATION.md`
- [x] Frontend tests/build, production UI validator, and real Chrome evidence pass — Evidence: `EVIDENCE/ui-contract.json`, `EVIDENCE/playwright-execution.json`
- [x] Independent review has no unresolved BLOCKER/HIGH — Evidence: `11-REVIEW.md`
- [x] Claude review has no unresolved BLOCKER/HIGH — Evidence: `CLAUDE-REVIEW.md`
- [x] Atomic commit is visible on the configured GitHub remote — Evidence: `SUMMARY.md`
