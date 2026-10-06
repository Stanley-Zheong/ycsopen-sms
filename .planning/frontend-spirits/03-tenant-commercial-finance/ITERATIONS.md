# Spirit 03 Iterations

| Iteration ID | Trigger or finding | Evidence | Change made | Affected behavior/decision | Recheck |
|---|---|---|---|---|---|
| FE03-I-001 | PRD V2 finance and trial TODOs | `docs/PRD_V2.md` | Defined separate commercial state model | FE-SPIRIT-03-LIFECYCLE, FE-SPIRIT-03-LEDGER, DR-FE03-001 | Pending implementation |
| FE03-I-002 | Issue `#122` exposed free-form tenant/price entry and no trial evidence | Live issue, Phase 37 implementation, source schemas | Froze and implemented a tenant-bound workbench, source-backed analysis, server eligibility, active pricing, and failure-preserving confirmation | FE-SPIRIT-03-TRIAL-CONVERSION, DR-FE03-002 | Focused Java and Vitest passed; final Chromium/full gates pending |
