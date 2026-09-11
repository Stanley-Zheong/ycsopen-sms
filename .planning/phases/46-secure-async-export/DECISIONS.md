# Phase 46 DECISIONS

- Reuse `export_tasks` as the canonical job table to avoid creating a second export system.
- Keep object storage out of scope; encrypted bytes are persisted in the job row for the first implementation.
- Preserve existing legacy export request response shapes where pages already depend on them.
- Use literal `data-testid` values in React; dynamic IDs are avoided because contract validation must detect selectors statically.
- Validate with current local Chrome only.
