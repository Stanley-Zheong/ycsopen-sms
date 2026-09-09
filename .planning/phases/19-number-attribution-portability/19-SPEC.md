# Phase 19 Spec

## Requirements

- `REQ-F-5-7`: number attribution and portability.
- `REQ-F-13-4`: carrier prefix management.

## Owned obligations

- `OBL-F-5-7-A`: longest-prefix local data returns carrier, province, and city from validated versioned 3-to-7-digit mappings.
- `OBL-F-5-7-B`: current portability result overrides prefix carrier only within declared freshness and keeps source.
- `OBL-F-5-7-C`: provider failure returns deterministic prefix fallback with degraded source.
- `OBL-F-13-4-A`: operators maintain carrier prefix mappings through validated full-batch and incremental updates with version/conflict evidence.

## Acceptance

The phase is complete only when `TODO.md` has no unchecked item and executable evidence covers all owned obligations.
