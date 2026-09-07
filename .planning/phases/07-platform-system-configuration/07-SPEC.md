# Phase 07 — Platform system configuration

## Intent

Deliver one production administration surface for typed platform settings whose draft, activation, reload, rollback, authorization, concurrency, and audit behavior is explicit and verifiable.

## Scope

In scope: an allow-listed typed key registry, safe defaults, validation, secret-reference classification, immutable versions, draft staging, optimistic activation, single-process atomic hot reload, rollback-as-new-version, change history, exact permissions, and the desktop Chrome administration page. Out of scope: arbitrary user-defined keys, plaintext secret storage, distributed configuration propagation, a generic policy engine, mobile layouts, and non-Chrome browser support.

## Behaviors

- `platform-system-configuration-01`: an authorized administrator reads the active typed registry, stages a validated change set against the current version, activates it only when the expected version is current, observes the reload result, and can roll back by creating a new version from history. The server merges changes into its unmasked active snapshot and persists a complete canonical version; clients never round-trip masked placeholders. Unknown keys, invalid values, stale writes, and unsafe secret values are rejected without runtime drift. Every stage, activation, rejected activation, and rollback remains attributable in version history and Phase 06 operation audit.

## Owned obligations

- OBL-IA-ADMIN-SYSTEM-CONFIG

## Completion rule

Complete only when the owned obligation has executable backend, MySQL, frontend, and installed-Chrome evidence; independent and Claude reviews contain no BLOCKER/HIGH; the production UI validator passes; and `TODO.md` has no unchecked item.
