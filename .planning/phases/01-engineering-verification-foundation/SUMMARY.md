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
- Delivery uses the issue-scoped branch and binds its final reviewed commit with the deterministic annotated delivery tag. Corrective commits are permitted until the TODO set is empty; the tag target is the delivered Phase 1 state.
- The PR trigger is the explicit GitHub Actions `pull_request` event. The required portable registry runs on Ubuntu and must pass without launching or downloading a browser.
- The external annotated tag is the final source of truth for commit, tree, PR check locator, actor, and PASS status.
- Phase 1 closes only its seven engineering-foundation obligations. Phase 56 product acceptance, business modules, and unsupported-browser coverage remain outside this delivery.

## Sealed subject and evidence

- Subject manifest path: `.planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json`
- Subject inputs: 194 — implementation 103, test 31, config 13, contract 35, validator 12.
- Subject manifest digest: `94d9fa6dabd318e7e651a622182e357ec11c539ab25505d59eae9da475b5dd53`
- Tested subject digest: `c3118bfde865d59b918c3032ae93570fc946d32eda043aed21801405963098e4`
- Evidence manifest path: `.planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json`
- Evidence manifest SHA-256: `7174fecb6ac826a1f91b3a0b3f9fb0d9ae8280674c52f4fbdd1cfe5d8c606579`
- Local Chrome runtime SHA-256: `05535c103fda9308f5dde454a8dbbf311f25292311c6dba399afbb9541d1bfde`
- GSD goal verification SHA-256: `e896e8d42686fc7a70efa88d5ac393d3a33feec9cf66edcbc54db106c0cf968a`
- GSD code review SHA-256: `7721fda63366691d91ab35ad547d00d87d920ded37d9a0cdb4455e6d95f9772c`
- Claude review SHA-256: `fdeab34ef761726981b345e34c069fb23b58ae55151798485b9039f3d29a615d`

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

- GitHub Actions run `33352694672` executed the portable registry on Ubuntu and passed 19 checks before correctly blocking on stale committed input hashes. Corrective commit `2223234` then passed the complete required check in run `33363729675`, job `99400024900`.
- The bounded synthetic screenshot policy needs an explicit superseding decision before reuse as a later-phase evidence convention.
- The three minimal schema validators, OCI pin recapture workflow, and platform-conditional Chrome-denial automation remain maintainability improvements, not current acceptance claims.
- Existing dependency advisories and the recorded Flyway/MySQL support warning are not represented as resolved by Phase 1.
- The PR is delivery evidence; merging it is outside this phase's completion contract.

Completion is determined only by the effective Phase 1 TODO query. No schedule, estimate, percentage, or final commit identity is asserted here.
