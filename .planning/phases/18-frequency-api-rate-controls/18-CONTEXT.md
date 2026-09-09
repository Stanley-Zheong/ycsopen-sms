# Phase 18 Context

Phase 18 implements `frequency-api-rate-controls`.

Dependencies used:

- Phase 2: shared Admin shell, page style, stable `data-testid` contract, local Chrome only.
- Phase 9: tenant API keys already store per-second/per-minute/per-hour/per-day limits.
- Phase 16: risk-control navigation and evidence style.
- Phase 17: runtime checker pattern, import/export request pattern, dry-run vs production evidence separation.

Scope fence:

- In scope: frequency rule CRUD/import/export request, rule metrics, Redis atomic fixed-window counters, scoped exemptions, API key four-window 429 precheck.
- Out of scope: channel TPS windows, final export file production, queue worker implementation, external gateway load testing.
