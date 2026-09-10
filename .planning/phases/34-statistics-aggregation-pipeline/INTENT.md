# Phase 34 Intent

Build the smallest useful aggregate foundation for later statistics pages and reports.

Design intent:

- Keep formulas explicit in a registry.
- Keep aggregates rebuildable and source-backed.
- Keep late/corrected data visible through `source_version`, `correction_identity`, and `quality_state`.
- Avoid introducing schedulers, dashboard UI, custom report DSLs, or broad browser validation in this backend-only phase.
