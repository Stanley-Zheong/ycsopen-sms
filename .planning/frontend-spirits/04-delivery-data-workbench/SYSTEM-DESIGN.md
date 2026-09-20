# Spirit 04 System Design

## Data Flow

Workbench pages consume detail, aggregate, status-code, export, and callback APIs. Each view model preserves status domain, source, freshness, and completeness information.

## Command Flow

1. User selects a row, group, or explicit query snapshot.
2. Page computes target eligibility and completeness.
3. Confirmation displays included target count, excluded filters, limits, and required reason.
4. Submission sends an idempotent command or records why idempotency is not available.
5. Page refreshes result state and exposes success or failure.

## Failure Model

Bulk actions are disabled when loaded targets are incomplete, over limit, still loading, or failed to load. Export failures appear in the export center or the invoking page as required by the route contract.

## Verification Model

Tests cover request payload, disabled states, target count, excluded filters, and retry behavior for each selected command.
