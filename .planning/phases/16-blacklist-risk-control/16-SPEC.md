# Phase 16 Spec

## Goal

Whitelist, blacklist, and third-party risk sources produce deterministic pre-task decisions with exact reason evidence.

## Scope

- Manage system and tenant black/white-list entries without storing protected-number plaintext.
- Import entries with partial-failure evidence and expose export-request metadata without generating final export files.
- Evaluate pre-task decisions before routing: tenant whitelist first, then system blacklist, tenant blacklist, third-party risk.
- Configure third-party risk provider URL, protected credential reference, level, score threshold, timeout, cache-or-allow fallback.
- Record provider success/failure/degraded decisions and expose interception analytics and appeal records.

## Out of scope

- Unsubscribe-origin creation.
- Final export file generation.
- Real external provider SDK integration.
- Runtime content safety and frequency controls, owned by later phases.
