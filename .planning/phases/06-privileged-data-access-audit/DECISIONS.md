# Decisions

## DR-06-001 — One audit store and one event handoff

Status: Accepted

Use one Phase 06-owned append-protected administrative operation table and one deduplicated security-event table. The legacy `operation_logs` table remains untouched and receives no new writer. Do not introduce a message broker, rule engine, SIEM adapter, or archive subsystem in this phase.

## DR-06-002 — Structural request summaries

Status: Accepted

Audit method, normalized route, parameter names, and bounded metadata. Never store request values or bodies. This loses forensic payload detail deliberately to eliminate secret-leak risk.

## DR-06-003 — Temporary reveal means response lifetime

Status: Accepted

Return revealed phone plaintext only in a no-store response after live authorization and purpose validation. The browser holds it only in component state and clears it on close; no durable reveal token or cache is introduced.

## DR-06-004 — Pencil fallback is evidence, not a false claim

Status: Accepted

Reuse the approved visual baseline asset unchanged because Pencil Desktop/MCP is unavailable. Phase-specific design is represented by HTML and the element/action catalog. Recreate the same screens in Pencil later only if the tool becomes available; it is not a code-completion blocker.

## DR-06-005 — Preserve legacy schema ownership

Status: Accepted

The V1 `operation_logs` table is inside the legacy Phase 1 schema prefix and cannot be safely extended by a V1500 migration under the repository ownership validator. Phase 06 therefore creates `privileged_operation_audits` under its own namespace and treats it as the sole active console audit store. There is no dual write, migration backfill, or claim that the legacy table is current.

## DR-06-006 — Keep database immutability with one explicit MySQL prerequisite

Status: Accepted

Keep the two small update/delete rejection triggers instead of replacing database protection with a larger audit subsystem. When binary logging is enabled, MySQL environments must set `log_bin_trust_function_creators=ON` before V1500 rather than granting `SUPER` to an application or migration account. The Flyway callback and local initialization script fail fast on a mismatched setting; the container harness executes the accepted path.

## DR-06-007 — Persist intent before executing the console handler

Status: Accepted

Insert a `STARTED` audit row in `preHandle`; if that insert fails, do not enter controller code. Finalize through one narrowly constrained definer procedure. A terminal outage may leave `STARTED`, which is an explicit searchable unfinished result, but it cannot make an executed operation completely disappear. This is smaller than a broker/outbox and closes the actual failure boundary.

## DR-06-008 — Separate migration and runtime database principals

Status: Accepted

Flyway owns DDL and post-migration grants. The application account has per-table privileges, only `SELECT/INSERT` on audit/event stores, and `EXECUTE` on the one terminal procedure. Real MySQL tests use the runtime account to prove direct update, delete, truncate, trigger drop, and table drop all fail.

## DR-06-009 — Trust only a same-host reverse proxy

Status: Accepted

Resolve forwarding headers only when the direct peer is loopback and the header contains exactly one numeric address. The documented same-host Nginx overwrites `X-Forwarded-For` with `$remote_addr`; append-style chains are rejected. A future cross-host or multi-hop proxy requires an explicit allowlist and its own verification.

## DR-06-010 — Catalog controls, actions, and states individually

Status: Accepted

Keep the six PRD-owned canonical UI rows and inventory all other Phase 06 controls/actions/states as supplemental rows. Repeating result-row IDs are paired with stable `data-row-key` values; singleton actions use literal unique IDs. The production contract now traces 53 selectors without creating extra gates.
