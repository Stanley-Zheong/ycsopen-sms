# Decisions

- Reuse existing protected blacklist write/read boundary instead of replacing encryption/blind-index code.
- Store and display only masked mobile text plus protected lookup locator; never expose `mobile_encrypted`.
- Implement provider integration as a deterministic local contract and cache/fallback recorder, not a real external SDK.
- Keep export as an auditable export-request response; final file export remains out of scope.
