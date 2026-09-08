# Phase 10 Context

## Dependency evidence

- Phase 02 supplies the Admin channel-configuration page registry, shell,
  tokens, and selector conventions.
- Phase 03 supplies the protected-field envelope codec and key-reference
  contract for channel account/password values.
- Phase 06 supplies redacted operation audit and privileged mutation rules.
- Entry validation must confirm these dependencies have a passing verification,
  empty TODO, and SUMMARY before implementation starts.

## Current implementation facts

- The legacy `channels` table already stores protocol, operator, endpoint,
  protected account/password columns, SP identifiers, connection/window,
  price, priority, active window, extension JSON, status, pause, and recovery
  timestamps (`core/src/main/resources/db/migration/V1__init_schema.sql`).
- `Channel` and `ChannelController` currently expose only a partial read/write
  projection and the existing `/admin/channels` page is a placeholder-quality
  list with pause/resume controls; Phase 10 must replace this path with the
  registered configuration route without serializing the entity or secrets.
- Existing tables that can declare channel dependencies include
  `route_rules`, `channel_group_members`, and
  `signature_channel_registrations`; `message_tasks.channel_id` is an active
  work dependency. No protocol socket or provider session is part of this
  phase.
- The repository has no phase-local channel version registry, activation
  snapshot, or dependency migration service; these are the new owned seams.

## Locked decisions

- D-10-001: Acceptance is installed desktop Google Chrome at 1440x900 only;
  no mobile layout, browser matrix, downloaded browser, ChromeDriver, or
  alternate browser project.
- D-10-002: Reuse Phase 03 protected-field encryption and Phase 06 redacted
  audit; never expose or log account/password plaintext and never serialize a
  `Channel` entity directly.
- D-10-003: A channel becomes effective only through a validated immutable
  configuration version and an atomic in-process snapshot swap. A failed swap
  retains the previous effective version, records a safe reason, and can be
  retried explicitly.
- D-10-004: Retirement is dependency-first. Inventory every declared route,
  pool, filing, price, and active-task reference; offline/delete is rejected
  until each reference has an explicit destination or is resolved.
- D-10-005: Protocol connectivity is represented by a small conformance
  adapter interface and deterministic test adapter; this phase validates the
  adapter contract but does not implement CMPP/SGIP/SMGP/HTTP sessions.

## Scope guard

This phase owns configuration data, validation, version activation/rollback,
dependency discovery/migration, and offline/delete. Health scoring, channel
pools, durable task migration, routing policy, provider status normalization,
and real protocol interoperability remain outside this phase and must not be
added to its plans.

## Source audit

| Source | ID | Feature or requirement | Plan | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| GOAL | — | Only complete protected channel configurations become effective | 10-01, 10-02 | COVERED | Versioned protected DTO, validation, and atomic activation are planned. |
| REQ | REQ-F-4-1 | Operators configure complete protocol/carrier/credential/connection/price/priority/availability data | 10-01, 10-04 | COVERED | Backend contract plus Admin form. |
| REQ | REQ-F-4-2 | Validated channel changes take effect without restart | 10-02 | COVERED | In-process snapshot and rollback evidence. |
| REQ | REQ-F-4-4 | Retirement detects dependencies and migrates them before offline | 10-03, 10-04 | COVERED | Inventory service and migration wizard. |
| RESEARCH | — | Existing `channels` table and protected-field readers must remain compatible | 10-01 | COVERED | Additive V1900/V1901 claims and safe projections. |
| RESEARCH | — | Existing route/pool/filing/task references are retirement inputs | 10-03 | COVERED | Explicit dependency provider list. |
| CONTEXT | D-10-001 | Chrome-only desktop acceptance | 10-04 | COVERED | Prototype and production test contract. |
| CONTEXT | D-10-002 | Protected secrets and redacted audit | 10-01, 10-04 | COVERED | Encryption boundary and UI masking. |
| CONTEXT | D-10-003 | Atomic version activation and rollback | 10-02 | COVERED | No restart, old version retained on failure. |
| CONTEXT | D-10-004 | Dependency-first retirement | 10-03 | COVERED | No offline/delete with unresolved references. |
| CONTEXT | D-10-005 | Adapter conformance without protocol sessions | 10-01 | COVERED | Deterministic adapter fixture only. |
