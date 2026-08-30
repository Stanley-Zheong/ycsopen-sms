# Authoritative Phase 1 TODO

Every checked item must cite executable evidence. Entry begins with every item open.

## Owned obligations

- [x] OBL-FOUND-TRACE-001 — Prove missing owner/behavior/test/evidence fields fail closed. Evidence: `EVIDENCE/OBL-FOUND-TRACE-001.json`, PASS, SHA-256 `ea269c21aa9f086fd5af089a1f244c135bef1661bec7964c82c0424f9602037f`.
- [x] OBL-FOUND-TRACE-002 — Prove bidirectional orphan and duplicate ownership diagnostics. Evidence: `EVIDENCE/OBL-FOUND-TRACE-002.json`, PASS, SHA-256 `06f47d3a707f95558b1ec6997a512a1bb143ef6d1108a7f53a33d94ecd5f0cf7`.
- [x] OBL-FOUND-TRACE-003 — Prove deterministic backend/frontend/database/cache/browser/copy/viewport/timezone command evidence. Evidence: `EVIDENCE/OBL-FOUND-TRACE-003.json`, PASS, SHA-256 `5e894fe633ddce694b1e62dd28af4713950020bc9d01573d0c4a560fe97641f4`.
- [x] OBL-FOUND-TRACE-004 — Prove artifact, entry review, TODO-empty, GSD/Claude review, and remote delivery gates fail closed. Evidence: `EVIDENCE/OBL-FOUND-TRACE-004.json`, PASS, SHA-256 `9c93e3426a319b4562bede7b1a73ad94dc96beaddeb0476a407994850170d6ae`.
- [x] OBL-FOUND-UI-DRIFT-001 — Prove missing and stale route/manifest/DOM/test-ID/Playwright references fail in both directions. Evidence: `EVIDENCE/OBL-FOUND-UI-DRIFT-001.json`, PASS, SHA-256 `08a68943f4821e17c4fab63e9d1b80f265266ef493dc1c643f46df45f6679f58`.
- [x] OBL-FOUND-UI-DRIFT-002 — Prove semantic row selector and separate non-sensitive key enforcement. Evidence: `EVIDENCE/OBL-FOUND-UI-DRIFT-002.json`, PASS, SHA-256 `da28c5b57415032ed1c76c9cc0a5ce7abc11f09769f6d6886e133bbe4b57443c`.
- [x] OBL-NFR-BROWSER — Execute and verify the current standard-path local Google Chrome at 1440x900 as one real smoke-and-visual scenario, recording path/version/brand/launch evidence. Evidence: `EVIDENCE/OBL-NFR-BROWSER.json`, PASS, SHA-256 `a11ef1aeb1ac3c7ede6b1dcb61be2c21b01f006496a47730e24ac37fc3eacf47`; runtime SHA-256 `dbd8e12d14a731fdd6b643fb887518885f744a211447852ea4cf226830eba3c3` records Google Chrome `151.0.7922.174` at `1440x900`.

## Entry, verification, review, and delivery

- [x] Complete `01-00` inside its exact 14-file hard cap: retain the independently proven `local-chrome-entry.json` primitive and migrate the six active evidence/lifecycle/runner/delivery consumers away from browser-source admission, dual probes, attestation, legacy digests, and removed contract APIs. Evidence: repository 18, lifecycle 20, delivery 29, runner 11, producer 14, bootstrap fixture 17, planning and catalog checks PASS; `01-00-SUMMARY.md` records the exact scope.
- [x] A reviewer distinct from the consumer-migration executor reruns all eight ENTRY criteria and the revised real bootstrap validates all 13 plans with zero exit. Evidence: `ENTRY-REVIEW.md` records reviewer `phase1_plan00_entry_reviewer2`, `8 PASS / 0 BLOCKER`, review SHA `0f43058d4002faffb12839734ca47c5938951c0765d9627e4ea5ba24f6079024`; real bootstrap PASS with seven obligations and 13 plans.
- [x] Plan checker has no unresolved blocking finding for the revised local-Chrome plans. Evidence: independent checker cycle 3 returned `VERIFICATION PASSED` for the 14-file Plan 00 revision and 13-plan dependency graph.
- [x] GSD goal verification has no unresolved blocking finding. Evidence: `01-VERIFICATION.md` is PASS, 7/7, BLOCKER/HIGH 0/0, report SHA-256 `f25f2125983a088d570f1ca141b9dcca1979f7729bafde6f571271c3833de4fe`.
- [x] GSD code review has no unresolved blocking finding. Evidence: `01-REVIEW.md` is PASS, BLOCKER/HIGH/WARNING 0/0/0, report SHA-256 `9ad8b386cff71f9efbafbe6418fa394ec8b261249d19a9fa758ec522780e0e05`.
- [x] Claude final review has no BLOCKER or HIGH finding. Evidence: `CLAUDE-REVIEW.md` Attempt 2 is PASS with BLOCKER/HIGH 0/0, bound to the current 194-input subject; report SHA-256 `634a9bf1220731e44ea9da5103b7925481a4f822885d8f29b95a9642a4b850b9`.
- [x] Every TEST-MATRIX command passes and each evidence target exists. Evidence: local registry 19/19 PASS, portable registry 20/20 PASS, exact-seven manifest SHA-256 `18db7794e68b8305103380c537710e32d07b76ecd0f9760a68f1b73e0d1243c4`, and all seven target checksums validate.
- [x] Scoped TODO query returns no unchecked item. Evidence: pre-push lifecycle validation passes with the single self-referential remote-delivery row explicitly reserved for external attestation.
- [ ] One atomic Phase 1 commit is visible on the configured GitHub remote and recorded in SUMMARY.md. Evidence: not recorded.
