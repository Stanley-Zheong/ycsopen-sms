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
- Canonical subject-manifest digest: `52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683`
- Serialized subject file SHA-256: `4ab907f8f6897533e3967a657ebaf0b57e3d68f141a54a56489793fea7cb1c68`
- Tested subject digest: `10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe`
- Root registry digest: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Root aggregate result digest: `84d6b662a53902b3efbff3ec761dc7b3c4cf71d13a8386b8b3a81ad107db9be1`
- Evidence manifest path: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json`
- Evidence manifest SHA-256: `a9ac4a5b5b1df2a931d39d0a4c356e786418151c094182e8b2682c4e2478ee61`
- GSD goal verification SHA-256: `4a75fc5d4c561298c630a508715c92680fb5a16195528635d65c45e2dff22f11`
- GSD code review SHA-256: `a4d78f2144e468c304018248988405ae6ef47c565fbfe30ba53593c57714402b`
- Claude review SHA-256: `13ebf7f5bac86231d22903d230610400028febf844dc60fb36e7443dbbe754a7`

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
