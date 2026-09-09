# Phase 19 Design

## Runtime

- `number_prefix_versions`: import version, update type, source, row count, conflict count, actor.
- `number_prefix_mappings`: active 3-to-7 digit prefix rows with carrier/province/city.
- `mobile_portability`: protected cache keyed by `mobile_hash`, exposing only masked mobile in UI/API rows.
- `NumberAttributionService.lookup`: checks longest prefix and applies fresh portability cache override.

## UI

- `admin-number-attribution-portability-attribution-page`: combined operational page.
- `admin-number-attribution-portability-prefixes-page`: prefix import/version table.
- `admin-number-attribution-portability-portability-page`: protected portability cache table and save action.
- `admin-number-attribution-fallback-source`: source/freshness/degraded trace for automation.
