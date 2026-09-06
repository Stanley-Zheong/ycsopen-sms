# Plan 03-22 Summary

## Outcome

The fixed Phase 3 root now composes exactly 14 deterministic and real-service lanes with `FAIL > BLOCKED > PASS` dominance. The final post-Claude-fix subject passed every lane, including default Maven, destructive validator fixtures, source audit, production reachability, real MySQL/MinIO/SoftHSM integration, protected-inventory acceptance, durable artifact leak scan and fixture cleanup.

The evidence producer consumed only that canonical result set and generated exactly four obligation results plus the tested-input and manifest documents. Its validator rejects changed subjects, missing or relabelled lanes, altered results, unresolved inventory, unsafe durable content, broken obligation traces and omitted cleanup.

## Bound results

- Subject manifest: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json`
- Subject inputs: 316
- Canonical subject-manifest digest: `8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2`
- Serialized subject file SHA-256: `04c123056eb6ecf97340dbebf4c52719b00c620b36d5eab63350a35b6cd86759`
- Tested subject: `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a`
- Registry: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Root result: `6fa4a45c604071b3f5e8118c7334071c281275c02c54dbc729a66e8a6a8fd1b1`
- Accepted inventory: `9d31954a3a4c01709b4db6be783d74ef0aed10c0ecbd2578b6719b37dc7c3009`
- Complete leak result: `320287b41fcd914d4446fe9c81037e65e319b30ef3578c2bfaae4121802cbb81`
- Evidence manifest SHA-256: `d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906`

## Exact-four evidence

| Obligation | Evidence digest | File SHA-256 | Result |
| --- | --- | --- | --- |
| OBL-CRYPTO-STORAGE-001 | `d8150937efce8af258eb5eeb6486fb742539fe6add9a23511ee4da5a52b3e5fe` | `86e32e172e201aac329ae791fc58ad3c463b2e44dab322fee749659c1fc8c660` | PASS |
| OBL-CRYPTO-STORAGE-002 | `f4046d271b81db2cba930aafd4aa3543cd4669ce703546433a9059790d144ee9` | `8221ee663db643fa7b267b8b71b4f3ea9915013c1afc63723100977fd4a66bda` | PASS |
| OBL-CRYPTO-STORAGE-003 | `806dd787a932cb92bb34c45b58c27153e51aac76f40175a81921c1659fd72307` | `5f91c6d6c799cf12ce058e10880410e02015a4f40863b9fc32336d28e318bf8f` | PASS |
| OBL-CRYPTO-STORAGE-004 | `5508e09046b333b26684c05e36a436785ca3fdaf5953932ddfdafc26239c4b89` | `c3bfb0bdda1fdc9c5e6169df2c7e97aa8fe0eb3d81fbd981166f36c584570c2e` | PASS |

## Final correction cycle

Claude Attempt 6 found two product defects missed by Round 9; both remain corrected and regression-covered. GSD Round 11 then closed the CI/delivery trust root. Three GitHub replays exposed, in order, a test-only Linux `/tmp` authority mismatch, an undeclared `rg` executable, and a mismatch between the synthetic PR merge checkout and the branch-head evidence subject plus absent ignored result files. Phase 3 now checks out the immutable PR head and commits exactly the 21 sanitized result inputs needed by clean-checkout validation; its destructive suite rejects removal of that binding. No production policy changed. The root and evidence above were regenerated after the corrections. Goal verification is PASS at 4/4, GSD Round 15 and Claude Attempt 12 both confirm BLOCKER/HIGH 0/0.

## Verification

- `./scripts/verify-phase-03 --all --result-root core/target/phase03/results` — PASS, 14/14 lanes.
- Evidence producer — PASS, 4 obligations and 6 files.
- Exact-four evidence validator — PASS, 4/4.
- Phase 3 fixture cleanup — PASS.
- Independent GSD verification — PASS, 4/4.
- Independent GSD code review Round 15 — PASS, BLOCKER/HIGH 0/0.
- Claude closure review Attempt 12 — PASS, BLOCKER/HIGH 0/0.
