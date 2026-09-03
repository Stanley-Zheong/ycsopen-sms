# Plan 03-22 Summary

## Outcome

The fixed Phase 03 runner composes 13 deterministic and real-service lanes with `FAIL > BLOCKED > PASS` dominance. The current subject passed every lane, including default Maven, destructive validators, source audit, real MySQL/MinIO/SoftHSM integration, protected-inventory acceptance, durable leak scan and cleanup.

The evidence producer consumes only this canonical result set and generated exactly four obligation results plus the tested-input and manifest documents. The validator rejects altered subjects, lane/result digests, inventory status, adapter identities, obligation traces, leak facts and cleanup omissions.

## Bound results

- Tested subject: `a9115b1e8a04f683a52604982552d220fac11006d4782be0d76b48e97097c875`
- Registry: `dbf8daca23828d80af0788b9b52288405c5258f1308089cf3ddefa455a2b8fa0`
- Root result: `34db6f90161a4a427550e25c79c7e1f71366c57c2f45246e998516d49add0b4c`
- Accepted inventory: `9d31954a3a4c01709b4db6be783d74ef0aed10c0ecbd2578b6719b37dc7c3009`
- Complete leak result: `b07527ef020c5717d2c00b8b6332e12a4eec71e916267f0f22a016539b4d0acc`

## Corrections

The first root attempt exposed stale run-owned resources, a broad phone pattern that could match digits inside a SHA-256 value, and one integration fixture missing the snapshot-recovery descriptor. Targeted fixes and affected real tests passed before the current-subject root run. After that run passed, the producer exposed an obsolete private inventory-disposition literal; its preflight and positive fixtures were aligned with the authoritative accepted `ADOPTED_*` contract, forcing one new subject-bound root run. Iterations I-033 and I-034 preserve both corrections without treating the invalidated results as evidence.

## Verification

- `./scripts/verify-phase-03 --all --result-root core/target/phase03/results` — PASS, 13 lanes.
- Evidence destructive suite — PASS, 59 cases.
- Evidence producer — PASS, 4 obligations and 6 files.
- Exact-four evidence validator — PASS, 4 obligations.
- Final artifact leak scan — PASS, 2 durable targets.
- Phase 03 fixture cleanup — PASS.
