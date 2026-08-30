# Phase 1 delivery locator

This document records stable delivery locators and the locally sealed verification identity. It intentionally does not predict or embed the final implementation commit SHA; the external annotated tag records and binds the final commit and tree after the required PR check passes.

Delivery remote name: `origin`
Delivery remote URL: `https://github.com/Stanley-Zheong/ycsopen-sms.git`
Delivery branch ref: `refs/heads/phase/01-engineering-verification`
Delivery tag ref: `refs/tags/ycsopen-sms/phase-01/delivery`
Delivery PR locator: `https://github.com/Stanley-Zheong/ycsopen-sms/pull/14`
Delivery required check: `Phase 01 portable registry`

## Scope and intent

- GitHub issue: `#13` — Phase 1 engineering verification and drift-control foundation.
- Delivery contains one atomic Phase 1 implementation commit on the issue-scoped branch.
- The PR trigger is the explicit GitHub Actions `pull_request` event. The required portable registry runs on Ubuntu and must pass without launching or downloading a browser.
- The external annotated tag is the final source of truth for commit, tree, PR check locator, actor, and PASS status.
- Phase 1 closes only its seven engineering-foundation obligations. Phase 56 product acceptance, business modules, and unsupported-browser coverage remain outside this delivery.

## Sealed subject and evidence

- Subject manifest path: `.planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json`
- Subject inputs: 194 — implementation 103, test 31, config 13, contract 35, validator 12.
- Subject manifest digest: `5380d59434c456cf1b8ab35e2485b5b75fcfbae8ef12720f2644f136119763b6`
- Tested subject digest: `9fcb0d747c9b2123212be7b1e5d812c9dff8568db6ea0cb0234549d44afa0e54`
- Evidence manifest path: `.planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json`
- Evidence manifest SHA-256: `18db7794e68b8305103380c537710e32d07b76ecd0f9760a68f1b73e0d1243c4`
- Local Chrome runtime SHA-256: `dbd8e12d14a731fdd6b643fb887518885f744a211447852ea4cf226830eba3c3`
- GSD goal verification SHA-256: `f25f2125983a088d570f1ca141b9dcca1979f7729bafde6f571271c3833de4fe`
- GSD code review SHA-256: `9ad8b386cff71f9efbafbe6418fa394ec8b261249d19a9fa758ec522780e0e05`
- Claude review SHA-256: `634a9bf1220731e44ea9da5103b7925481a4f822885d8f29b95a9642a4b850b9`

All seven exact obligation summaries are PASS and are checksum-bound by the evidence manifest.

## Verification commands

Local verification and evidence sealing:

```sh
./scripts/verify-phase-01 --all --evidence-dir .planning/phases/01-engineering-verification-foundation/EVIDENCE
./scripts/verify-phase-01 --ci --evidence-dir .planning/phases/01-engineering-verification-foundation/EVIDENCE
/usr/bin/env ruby .planning/tools/validate-verification-evidence.rb --manifest .planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json --require-owner engineering-verification-foundation
/usr/bin/env ruby .planning/tools/validate-phase-lifecycle.rb --phase 01 --package engineering-verification-foundation --stage pre-push-exit --require-gsd-clear --require-claude-clear --allow-reserved-delivery
```

Live delivery validation after the PR check and annotated tag exist:

```sh
/usr/bin/env ruby .planning/tools/validate-delivery-attestation.rb --phase 01 --summary .planning/phases/01-engineering-verification-foundation/SUMMARY.md --evidence-manifest .planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json --require-pr-check-pass
/usr/bin/env ruby .planning/tools/validate-phase-lifecycle.rb --phase 01 --package engineering-verification-foundation --stage effective-todo-empty
```

## Browser support boundary

The only real-browser acceptance evidence is the currently installed standard-path Google Chrome at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`, observed as `151.0.7922.174`, using viewport `1440x900`. No Chrome, ChromeDriver, alternate Chromium bundle, Edge, Safari, Firefox, provider, tunnel, secret, VM, or browser version matrix is downloaded or required. Other browsers are unsupported and untested.

## Known limitations retained

- The GitHub-hosted portable run and real target-tree Ripper extraction are delivery-stage facts and are not claimed until the remote validator passes.
- The bounded synthetic screenshot policy needs an explicit superseding decision before reuse as a later-phase evidence convention.
- The three minimal schema validators, OCI pin recapture workflow, and platform-conditional Chrome-denial automation remain maintainability improvements, not current acceptance claims.
- Existing dependency advisories and the recorded Flyway/MySQL support warning are not represented as resolved by Phase 1.
- The PR is delivery evidence; merging it is outside this phase's completion contract.

Completion is determined only by the effective Phase 1 TODO query. No schedule, estimate, percentage, or final commit identity is asserted here.
