---
phase: 03-crypto-storage-bootstrap
reviewer: claude-code-cli-2.1.238
session: 83dc0072-0511-4869-b562-3038e3732489
mode: tool-less-phase-patch-review
attempt: 1
status: blocked
blocker: 4
high: 3
warning: 4
info: 3
---

# Phase 03 Claude plan and entry review

## Verdict

`BLOCKED` — execution is not authorized. This is a pre-implementation review of the Phase 03 patch from the Phase 02 closure base through entry approval. The nested reviewer had no tools and received only the committed Phase 03 patch.

## Findings

| ID | Severity | Affected contract | Finding | Required correction |
| --- | --- | --- | --- | --- |
| PH03-B01 | BLOCKER | `03-09-PLAN.md` | Message persistence writes `ycs_crypto_blind_indexes`, but the plan neither depends on nor follows `03-11`, which creates that V1200 table. Its unit test cannot reveal the missing schema. | Make V1200 schema creation an earlier dependency and run `03-09` only after it exists. |
| PH03-B02 | BLOCKER | `03-10`, `03-11`, `03-12`, `03-15` plans | Three plans depend on `03-11` while sharing its wave, contradicting the required strict dependency-wave order and allowing consumers to run beside schema creation. | Move `03-11` earlier or move every dependent plan later; revalidate every edge. |
| PH03-B03 | BLOCKER | `ENTRY-REVIEW.md` criterion `ENTRY-03-04-PLAN-STRUCTURE` | Entry review claimed zero dependency-wave violations despite PH03-B01/B02. The current PASS evidence is invalid. | Add a strict edge validator plus semantic table-producer/consumer wiring check, fix plans, and run a distinct entry review. |
| PH03-B04 | BLOCKER | DR-P03-009, `03-17-PLAN.md` | A “one-time” upload token is also required for up to five separate purpose uploads and replacements, leaving credential reuse semantics contradictory. | Define session-bound repeat-use semantics through expiry/closure and add sequential multi-purpose upload tests. |
| PH03-H01 | HIGH | DR-P03-001, `03-04-PLAN.md` | Envelope header fields are not explicitly authenticated with the canonical AAD, leaving version/algorithm/provider/key-reference substitution ambiguous. | Bind a canonical header encoding into data and wrap AAD; add one tamper mutation per header field. |
| PH03-H02 | HIGH | DR-P03-004/008, migration/recovery plans | Plans validate an encrypted-snapshot manifest but never perform a real snapshot restore, so the failure-safe rollback path is not exercised. | Add a real encrypted MySQL snapshot-and-restore drill or an equally executable ops-owned recovery contract with real restore smoke evidence. |
| PH03-H03 | HIGH | DR-P03-001, `03-04`, `03-15`, `03-16` plans | The DB envelope ceiling is concrete, but protected objects have no explicit envelope/allocation maximum before decoding or allocation. | Set purpose-specific maximum plaintext/envelope bounds and test rejection before allocation. |
| PH03-W01 | WARNING | `03-15-PLAN.md` | S3 adapter has an unnecessary `03-11` schema dependency while the actual blind-index consumer omitted it. | Derive dependencies from actual table/file/service use and remove false edges. |
| PH03-W02 | WARNING | DR-P03-007 | `bulk_sending_items` and `uplink_records` are present in protected inventory but absent from the exact blind-index target decision without an explicit exclusion reason. | Record why each is excluded or add it to migration coverage. |
| PH03-W03 | WARNING | DR-P03-002/003, `03-20-PLAN.md` | KEK rotation is administrative only; no usage-bound trigger limits wrap nonce collision exposure under a long-lived key. | Define a conservative per-key operation ceiling and make rotation admission fail closed when reached. |
| PH03-W04 | WARNING | DR-P03-008 | The Ed25519 manifest trust-anchor fingerprint has no compromise/rotation procedure. | Define active/retiring trust-anchor versions, rollout and rejection rules. |
| PH03-I01 | INFO | Envelope contract docs | `YCSE/v1` is repeated across research, decisions and plans, creating drift risk. | Make one contract artifact canonical and reference it. |
| PH03-I02 | INFO | `03-10-PLAN.md` | `HashUtil.java` deletion lacks a repository-wide reference audit. | Require a zero-reference command before deletion or retain a scoped legacy-only owner. |
| PH03-I03 | INFO | DR-P03-008 | Migration preflight CLI exists only in prose. | Add a machine-readable schema or golden `--help` contract. |

## Review record

- Invocation: `claude -p --output-format json --disable-slash-commands --tools ""`.
- Input: committed Phase 03 patch relative to Phase 02 closure commit `68ae156d2f705fa9f3df853ecd13378cd926e557`.
- Nested tool access: none.
- Returned counts: BLOCKER 4, HIGH 3, WARNING 4, INFO 3.
- Execution authorization: NOT AUTHORIZED.

## Final verdict

BLOCKED
