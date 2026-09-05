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
- Canonical subject-manifest digest: `ef4002d2d3a4518c38a52ba97a3e6f482441e8a54bdea82eb5dd082c1bc81bac`
- Serialized subject file SHA-256: `4a4cc5e45890fe5dccbe0a24b166b5b8f447f0b22f670161aca7eb783129c58c`
- Tested subject digest: `acdbdba8db25d3936cb9eb99310c1ee2e14f77f430574109d6c239a6906823c0`
- Root registry digest: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Root aggregate result digest: `9ddf7fa7de8cb49a6110f10cb309bd1bdbdeb7244074bf38cc240f9cdc5a9e3a`
- Evidence manifest path: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json`
- Evidence manifest SHA-256: `e6ce5f0998ccc4ef3e758e6fd92fa3487555bdea6cbe855103c55ccb3769bb01`
- GSD goal verification SHA-256: `cb75444bff6996b6d61e3229d76f90dc5db27c154ccb2f09a5def097525154a2`
- GSD code review SHA-256: `c07b831535575932891480ec21e4afd3e20d0bc7d1c849e5735ba96bf4a8c4fc`
- Claude review SHA-256: `881c2484facba40558c93f97816dbaaac61cb76f721d7f8a3b9dcf652d927650`

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
