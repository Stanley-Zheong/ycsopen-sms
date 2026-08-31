# Authoritative Phase 1 TODO

Every checked item must cite executable evidence. Entry begins with every item open.

## Owned obligations

- [x] OBL-FOUND-TRACE-001 — Prove missing owner/behavior/test/evidence fields fail closed. Evidence: `EVIDENCE/OBL-FOUND-TRACE-001.json`, PASS, SHA-256 `c7f2843aa382354b5e31e2686642380905d1f13515ae6dc7c66cc87d7ee68231`.
- [x] OBL-FOUND-TRACE-002 — Prove bidirectional orphan and duplicate ownership diagnostics. Evidence: `EVIDENCE/OBL-FOUND-TRACE-002.json`, PASS, SHA-256 `4f7b44e049a9a0bbbaff5a33053b3a449e5f0a29da91fbea43cac68457e639b1`.
- [x] OBL-FOUND-TRACE-003 — Prove deterministic backend/frontend/database/cache/browser/copy/viewport/timezone command evidence. Evidence: `EVIDENCE/OBL-FOUND-TRACE-003.json`, PASS, SHA-256 `1e3b569a2a266451d056537278cadae3fc3503dc7b4f8a7f36ce470dd6f28b5d`.
- [x] OBL-FOUND-TRACE-004 — Prove artifact, entry review, TODO-empty, GSD/Claude review, and remote delivery gates fail closed. Evidence: `EVIDENCE/OBL-FOUND-TRACE-004.json`, PASS, SHA-256 `61b7fa432f8812f61db83042793227ff1365d11d6d3b974458a9e36ca5744e26`.
- [x] OBL-FOUND-UI-DRIFT-001 — Prove missing and stale route/manifest/DOM/test-ID/Playwright references fail in both directions. Evidence: `EVIDENCE/OBL-FOUND-UI-DRIFT-001.json`, PASS, SHA-256 `cd1cf54ce9c99463a0b7e9ad1a64acc508741a41e424dca73a23c6dd401da053`.
- [x] OBL-FOUND-UI-DRIFT-002 — Prove semantic row selector and separate non-sensitive key enforcement. Evidence: `EVIDENCE/OBL-FOUND-UI-DRIFT-002.json`, PASS, SHA-256 `f1345158bd5a9ad43f3eb64247c94004029e0d91554c90c1bd13d7207e03579c`.
- [x] OBL-NFR-BROWSER — Execute and verify the current standard-path local Google Chrome at 1440x900 as one real smoke-and-visual scenario, recording path/version/brand/launch evidence. Evidence: `EVIDENCE/OBL-NFR-BROWSER.json`, PASS, SHA-256 `4a68849a2ef397105476df4e61f815669497d984aadce1d9f493cf4ccd767087`; runtime SHA-256 `05535c103fda9308f5dde454a8dbbf311f25292311c6dba399afbb9541d1bfde` records Google Chrome `151.0.7922.174` at `1440x900`.

## Entry, verification, review, and delivery

- [x] Complete `01-00` inside its exact 14-file hard cap: retain the independently proven `local-chrome-entry.json` primitive and migrate the six active evidence/lifecycle/runner/delivery consumers away from browser-source admission, dual probes, attestation, legacy digests, and removed contract APIs. Evidence: repository 18, lifecycle 20, delivery 29, runner 11, producer 14, bootstrap fixture 17, planning and catalog checks PASS; `01-00-SUMMARY.md` records the exact scope.
- [x] A reviewer distinct from the consumer-migration executor reruns all eight ENTRY criteria and the revised real bootstrap validates all 13 plans with zero exit. Evidence: `ENTRY-REVIEW.md` records reviewer `phase1_plan00_entry_reviewer2`, `8 PASS / 0 BLOCKER`, review SHA `0f43058d4002faffb12839734ca47c5938951c0765d9627e4ea5ba24f6079024`; real bootstrap PASS with seven obligations and 13 plans.
- [x] Plan checker has no unresolved blocking finding for the revised local-Chrome plans. Evidence: independent checker cycle 3 returned `VERIFICATION PASSED` for the 14-file Plan 00 revision and 13-plan dependency graph.
- [x] GSD goal verification has no unresolved blocking finding. Evidence: `01-VERIFICATION.md` is PASS, 7/7, BLOCKER/HIGH 0/0, report SHA-256 `b9d51a8b619594498e472c6e49c41946dd81281c8a448cda33f51f9293d4e3f8`.
- [x] GSD code review has no unresolved blocking finding. Evidence: `01-REVIEW.md` is PASS, BLOCKER/HIGH/WARNING 0/0/0, report SHA-256 `3f095b61dc1fd9a5bfed75cba396deb5054dfb9fe006d3eb252926935be684a1`.
- [x] Claude final review has no BLOCKER or HIGH finding. Evidence: `CLAUDE-REVIEW.md` is PASS with BLOCKER/HIGH 0/0, bound to the current 194-input subject; report SHA-256 `fdeab34ef761726981b345e34c069fb23b58ae55151798485b9039f3d29a615d`.
- [x] Every TEST-MATRIX command passes and each evidence target exists. Evidence: local registry 19/19 PASS, portable registry 20/20 PASS, exact-seven manifest SHA-256 `7174fecb6ac826a1f91b3a0b3f9fb0d9ae8280674c52f4fbdd1cfe5d8c606579`, and all seven target checksums validate.
- [x] Scoped TODO query returns no unchecked item. Evidence: pre-push lifecycle validation passes with the single self-referential remote-delivery row explicitly reserved for external attestation.
- [x] One atomic Phase 1 commit is visible on the configured GitHub remote and recorded in SUMMARY.md. Evidence: remote branch `refs/heads/phase/01-engineering-verification` and annotated tag `refs/tags/ycsopen-sms/phase-01/delivery` resolve to `7a5d0fad7220fe55b9690812b38e2a0e937b2fb7`; GitHub Actions run `33372720703`, job `99427243760` is SUCCESS; live delivery attestation and effective-TODO lifecycle both PASS.
