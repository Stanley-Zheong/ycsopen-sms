# Phase 3 delivery locator

This document records stable delivery locators and the locally sealed verification identity. It intentionally does not predict or embed the final implementation commit SHA; the external annotated tag binds the final commit and tree after the required PR check passes.

Delivery remote name: `origin`
Delivery remote URL: `https://github.com/Stanley-Zheong/ycsopen-sms.git`
Delivery branch ref: `refs/heads/phase/03-crypto-storage-bootstrap`
Delivery tag ref: `refs/tags/ycsopen-sms/phase-03/delivery`
Delivery PR locator: `https://github.com/Stanley-Zheong/ycsopen-sms/pull/15`
Delivery required check: `Phase 03 portable registry`

## Scope and intent

- Phase 3 protects the current database/object storage surfaces, owns purpose-separated opaque key use and rotation, and provides the signed, recoverable migration path.
- The four owned atomic obligations are exactly `OBL-CRYPTO-STORAGE-001` through `OBL-CRYPTO-STORAGE-004`.
- `MOBILE_BLIND_INDEX` means the blind index of a mobile phone number. This phase contains no mobile application, iOS or Android implementation.
- The PR is delivery evidence; merging it is outside this phase's completion contract.

## Sealed subject and evidence

- Subject manifest path: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json`
- Subject inputs: 316.
- Canonical subject-manifest digest: `8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2`
- Serialized subject file SHA-256: `04c123056eb6ecf97340dbebf4c52719b00c620b36d5eab63350a35b6cd86759`
- Tested subject digest: `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a`
- Root registry digest: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Root aggregate result digest: `6fa4a45c604071b3f5e8118c7334071c281275c02c54dbc729a66e8a6a8fd1b1`
- Evidence manifest path: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json`
- Evidence manifest SHA-256: `d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906`
- GSD goal verification SHA-256: `de60503f571f6d1a546d2ec3b82677ac5d682606fe1c0ea908076745f89f13f6`
- GSD code review SHA-256: `11d6b2f27f67118de6ab35e6215041e44d85cefbbf30e34fdd6b606a5949fe9d`
- Claude review SHA-256: `f10159bd3578a0fe30a73352b175791d35d9f33b9bd333b1830306d05630db4a`

All four exact obligation summaries are PASS and checksum-bound by the evidence manifest.

## Delivered behavior

- Current protected database writers/readers use context-bound YCSE envelopes and versioned phone-number blind indexes; accepted raw database/log samples contain no prohibited plaintext.
- Protected registration objects remain private ciphertext with bounded purpose/session/claim/capability lifecycle and no persisted raw URL.
- FIELD, phone-index, capability/upload digest and snapshot key references use purpose-first publication/retirement locks; exact live references block retirement and root keys never enter durable evidence.
- The production migration entry verifies signed canonical configuration and writer/snapshot pairs, performs resumable migration, and creates/authenticates/restores/deletes bounded encrypted MySQL snapshots through fixed trusted-client arguments.
- Production composition is shipped through Spring and ServiceLoader. Docker/MySQL process substitution exists only in test source and is excluded from the production JAR.

## Verification commands

```sh
./scripts/verify-phase-03 --all --result-root core/target/phase03/results
/usr/bin/env ruby .planning/tools/produce-phase-03-crypto-evidence.rb --phase-dir .planning/phases/03-crypto-storage-bootstrap --result-root core/target/phase03/results
/usr/bin/env ruby .planning/tools/validate-phase-03-crypto-evidence.rb --phase-dir .planning/phases/03-crypto-storage-bootstrap --require-owner crypto-storage-bootstrap
/usr/bin/env ruby .planning/tools/validate-phase-lifecycle.rb --phase 03 --package crypto-storage-bootstrap --stage pre-push-exit --evidence-manifest .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json --require-gsd-clear --require-claude-clear --allow-reserved-delivery
```

Live delivery validation after the required PR check and annotated tag exist:

```sh
/usr/bin/env ruby .planning/tools/validate-delivery-attestation.rb --phase 03 --summary .planning/phases/03-crypto-storage-bootstrap/SUMMARY.md --evidence-manifest .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json --require-pr-check-pass
/usr/bin/env ruby .planning/tools/validate-phase-lifecycle.rb --phase 03 --package crypto-storage-bootstrap --stage effective-todo-empty --evidence-manifest .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json --require-gsd-clear --require-claude-clear
```

## Accepted nonblocking boundaries

- SoftHSM proves the Java 21 SunPKCS11 protocol path, not certification of a particular physical HSM deployment.
- Production MySQL clients must be signed and installed under `/usr/bin` or `/opt/ycsopen/mysql-client`; the local real-service proof uses a test-only Docker process adapter because Homebrew paths are intentionally not trusted for production.
- Java path execution retains a final platform TOCTOU residual despite repeated root/owner/mode/ACL/inode/size/digest checks; the signed executable digest and immutable deployment root are the authenticity boundary.
- JDBC retry closures must remain free of externally visible non-transactional side effects. Current callers meet that contract.

Completion is determined only by the effective Phase 3 TODO query. No schedule, estimate, percentage or impossible same-commit identity is asserted here.

## Plan 31 corrective delivery

- Corrective implementation head: `a7039ed245bb33167a9e72e39fefcc8c0daea89e` on `refs/heads/phase/03-crypto-storage-bootstrap`.
- Tested pull-request merge: `f841497bf3635316c2cd58c86f9c296e5756ab5f` for PR 15.
- GitHub Actions run `34010664879`: Phase 01 supersession, Backend, Phase 03 portable contracts, Phase 03 real integration and Web all PASS.
- The real job executed seven named suites / eight tests with zero failures, errors or skips; owned service cleanup and Surefire upload also PASS.
- Plan 31 independent GSD and Claude reviews both finish with `BLOCKER 0 / HIGH 0`.
- Closure review reopened CR-12 because active `03-VALIDATION.md` still contradicts final executable state; Phase 03 remains open until that row is verified and the TODO query is physically empty again.

The earlier annotated tag is retained as historical evidence and is not moved. PR merge remains outside the Phase 3 completion contract.
