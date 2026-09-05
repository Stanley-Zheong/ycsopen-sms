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
- Canonical subject-manifest digest: `e04d225b74fa9c2a18ef9316987278bd370c70fa99b8cb0feeeb305a8e3e2eb1`
- Serialized subject file SHA-256: `12c9531010209cca14a8507ff9b7e3df21ef805811c9aaf3c07bfd21af83d843`
- Tested subject digest: `78ab379f0d1e55c34740a6e13962dd558579558f45441ed0d757d5b4a1eb1c7f`
- Root registry digest: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Root aggregate result digest: `2cdc39315467c747b7502b53db4c92597ee048db68a460a7e522038df6a18bef`
- Evidence manifest path: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json`
- Evidence manifest SHA-256: `9cad7d1610be6d473ebcc63647da2954427bcaf79b5feb45ddabaa16d80b23db`
- GSD goal verification SHA-256: `abb8b5f20fec8611ae9755f64f28b6b4c93592d22e4a9ecdd83e62bc77f91903`
- GSD code review SHA-256: `8d8146ac0104545c6c835f7685d35173d73e0a04f564c0d9a5e07bf2176ee41e`
- Claude review SHA-256: `fcfb85058372ad4b590b0a03f34621ab6cd2953eab68033d3cb8b03bd904c8e1`

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
