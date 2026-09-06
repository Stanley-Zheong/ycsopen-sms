# Phase 03 Plan 31: Claude review

## Verdict

`PASS` — product-code and corrected active-validation-contract reviews both have `BLOCKER 0 / HIGH 0`; the pushed contract head subsequently passed all five PR CI jobs.

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

## CR-09 delivery-environment review

Run `34008602025` proved the direct real-integration job did not prepare its locked Docker images on a clean hosted runner. After the root cause and correction were recorded in the solution, Claude reviewed only the four-file CR-09 delta.

- Session: `d822fa2c-f2d8-44cb-8881-703ca6960bc5`.
- Verdict: `BLOCKER 0 / HIGH 0`.
- Confirmed: the step runs before Maven, pulls only MySQL and MinIO, pins `linux/amd64` and immutable digests, fails closed under the Actions shell, preserves fixture validation, and does not restore the evidence runner or add Redis/implicit test downloads.
- Static boundary: Claude could not inspect whether the two digest strings matched the fixture constants. A repository-executed Ruby contract query confirmed exact equality for both MySQL and MinIO.

CR-09's image-preparation step passed in run `34008949254`; the overall real-integration boundary remained open because that run then exposed CR-10.

## CR-10 OCI identity review

Run `34008949254` proved image preparation works and exposed a classic-Docker portability defect: MinIO's pinned manifest-list digest and platform config/image digest were treated as the same value. The solution was updated before implementation to retain exact identity while supporting both Docker stores.

- Initial session: `033a9112-3f2b-487e-940c-717546042f5f`, `BLOCKER 0 / HIGH 0` on the two-representation allowlist and pre-start binding.
- An independent reviewer then found one valid HIGH: containerd output mislabeled the manifest digest as `config_digest`, and container mismatch lacked a regression.
- The correction makes `image_digest`, `config_digest`, and `image_id` unambiguous and adds an executable `MINIO_CONTAINER_IDENTITY_MISMATCH` case.
- Final session: `7144bb8c-594f-4e69-ad48-607313f64140`, `BLOCKER 0 / HIGH 0`.
- Independent correction review: PASS; service-contract test 15 cases / 70 assertions, zero failures, errors or skips.

CR-10 remains open only for the fresh classic-Docker synthetic-merge replay.

## CR-11 clean artifact-scan bootstrap review

Run `34009512062` passed image preparation and MinIO identity validation, then exposed a clean-output ordering defect: the real leak integration invoked the fail-closed artifact scanner before the current run had produced any report under `core/target/phase03`.

- Initial session `a7cccfd9-a681-4e1d-a1c7-2297c4e27ef1` reported one directory-existence BLOCKER and one duplicated-output-contract HIGH.
- The valid shared-contract finding was corrected by moving the closed PKCS#11 PASS grammar and sensitive-field rejection into `Phase03Pkcs11IntegrationTest.validatedSanitizedProof`, which both the producer and leak consumer now call.
- An intermediate explicit `createDirectories` response was rejected by the independent reviewer because it could follow an existing external `core/target` symlink before containment validation. That call was removed. Successful `startAll()` already proves the service-owned generated root exists; the leak test resolves it and requires exact equality with the repository path before writing.
- Final session `11172308-7970-4d0d-af9f-7cb9406d34a7`: `BLOCKER 0 / HIGH 0`.
- Final independent focused review: PASS, `BLOCKER 0 / HIGH 0`.
- Executable proof: artifact-scanner destructive fixtures 24/24 PASS; `mvn -f core/pom.xml -Pphase03-integration -Dtest=Phase03LeakScanIntegrationTest clean test` executes 1 test with zero failures, errors, or skips; the temporary `pkcs11-real-proof-*.txt` input is absent after completion.

CR-11 remains open only for the fresh synthetic-merge replay of all seven named real suites.

## CR-12 active validation-contract review

Closure review found that `03-VALIDATION.md` still described its planning-time state rather than the final executable topology. The solution was recorded before the document changed. The first bounded Claude review then correctly rejected an intermediate version that marked itself complete before its own review and CI existed.

- Session: `12a76fa1-2c9a-4a91-92da-f55313fdd8b7`.
- Initial verdict: `BLOCKER 2 / HIGH 2 / MEDIUM 2 / LOW 2`.
- Accepted findings: premature `complete`/Nyquist/approval state; a checked self-referential review/CI sign-off; undifferentiated historical sealed/current CI evidence; and an unexplained `DR-P03-012` reference.
- Corrective contract: keep validation `verifying` and Nyquist false until the exact correction commit has blocking-free independent/Claude review and PR CI; distinguish Plans 02/22/23 sealed evidence from current Plan 31 direct evidence; cite the actual verification/decision records; retain the fail-closed prerequisite rule.
- Separately, the independent reviewer found one stale positive reference to the intentionally deleted `FieldEncryptorTest`. The active map now runs retained `ProtectedFieldCodecTest` and asserts both retired files remain absent.

The corrected pre-CI contract then received two fresh bounded reviews:

- Independent final review: `BLOCKER 0 / HIGH 0 / MEDIUM 0 / LOW 0`.
- Claude session `d8e35bd0-f73a-437f-87c8-35fc857d88cf`: `PASS`, `BLOCKER 0 / HIGH 0 / MEDIUM 0 / LOW 0`.
- The reviewers confirmed `verifying`/Nyquist false, exactly one remaining external CI sign-off after review recording, distinct sealed/current evidence labels, the seven-suite override citations, the retained Plan 24 successor test and a non-self-referential two-commit closure.

Contract head `2a320e4f374dd1bf741dcc7724130b40f130f8dd` and synthetic merge `fcd93e3c926d67bfb2627f65981e7fc96649d638` passed run `34012096119` across Backend, Phase 1 portable, Phase 3 portable, Phase 3 real integration and Web. CR-12 is closed.
