# Phase 03 Plan 31: Claude review

## Verdict

`PASS` — final bounded review has `BLOCKER 0 / HIGH 0` after repository evidence and the independent GSD reviewer adjudicated every observation.

## Review boundary

Claude reviewed the Plan 31 remediation in bounded slices rather than the unrelated cumulative Phase 1-3 branch history:

- message migration/state validation;
- object publication, routing, frequency and CI;
- final migration correction after CR-06 through CR-08.

The calls were tool-less static reviews. Their observations were therefore treated as hypotheses and checked against schema, current writer/reader code, CI provisioning and executable tests before disposition.

## Material finding and correction

Claude identified one valid HIGH issue: whole-target message-state validation used an unbounded `SELECT ... FOR UPDATE`. The solution was recorded in `03-31-SOLUTION.md` before code changed. `MESSAGE_STATE_SCAN_SQL` now uses a transaction-consistent non-locking read, while bounded migration batches, per-row re-locking, complete-old-value CAS, target lease and checkpoint fences remain intact. Focused migration tests and the independent Round 4 review confirm the boundary.

## Rejected observations

| Observation | Disposition | Repository evidence |
| --- | --- | --- |
| MESSAGE_TASK query swapped `message_id` and `mobile_encrypted` | Not a finding | Select order, `MessageTaskSource`, V1 schema and the current writer all agree. |
| Array wiping aliases `LegacyRow` storage | Not a finding | Constructors and accessors clone the byte arrays; cleanup clears only accessor copies. |
| Real CI cannot build SoftHSM | Not a finding | The job installs the required compiler/CMake/OpenSSL dependencies; the pinned source manifest disables unrelated dependencies and the harness executes provision/init/preflight. |
| MOBILE fail-closed behavior needs tenant scoping now | Outside Plan 31 | The current rule entity is global and the locked correction intentionally fails closed for any active MOBILE rule; Phase 18 owns the stable identity and complete scope model. |
| BACKFILLED should admit all untouched legacy rows | Based on a state-machine misread | BACKFILLED is the completed-batch gate; untouched legacy rows must block it, while valid half-migrated rows are accepted until the current atomic transition finishes. |

## Review sessions

- Migration slice: `b9c81a10-e0c3-4043-acd5-7846b187e3f5`.
- Object/routing/CI slice: `39083d1e-1c20-40c9-a5a5-f9d96520e1d7`.
- Final migration slice: `bd6667af-8431-4be9-aca8-aa5e67a54808`.
- Reconsideration with repository facts: `5a055ad5-2731-49c4-af73-08886448b24e`.

The reconsideration closed the prior migration objection and returned `BLOCKER 0 / HIGH 0`. Independent GSD Round 4 then rechecked the same facts and also returned `PASS` with `BLOCKER 0 / HIGH 0`.

## Executable evidence

- Default `mvn -f core/pom.xml test`: 392 tests, 0 failures, 0 errors, 17 expected real-integration skips under the default profile.
- Focused migration: 11/11 PASS, zero skip.
- Real migration: 2/2 PASS, zero skip.
- Real object-storage suite: PASS with all four MySQL/InnoDB race interleavings and zero skip.
- Complete named Phase 03 real run: seven suites executed with zero failures, errors or skips.
- `git ls-files 'core/target/**'`: zero paths.
- `git diff --check`: PASS before report synchronization.

The remaining proof boundary is the GitHub synthetic-merge CI run after the corrective commit is pushed.
