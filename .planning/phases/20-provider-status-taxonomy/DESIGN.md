# Design

## Backend

- `provider_status_versions` stores active/superseded mapping versions.
- `provider_status_mappings` stores provider/protocol/code mapping rows.
- `provider_status_normalization_events` preserves immutable evidence of every normalization decision.
- `provider_status_export_requests` records export intent without implementing later secure download delivery.
- `ProviderStatusTaxonomyService` implements `ProviderStatusTaxonomyPort`.

## Frontend

- `/admin/status-codes` provides version history, current mapping rows, import form, normalization trial, and export request action.
- Test IDs are explicit and documented in `UI-ELEMENTS.md`.

