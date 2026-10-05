# Spirit 04 System Design

## Data Flow

Workbench pages consume detail, aggregate, status-code, export, and callback APIs. Each view model preserves status domain, source, freshness, and completeness information.

For the admin uplink workbench, `UplinkNormalizationService` owns the tenant identity projection. Uplink and push-monitor queries join `tenants` once and return the stable internal ID plus institution number, short name, and full name. The React page renders those response fields directly; it does not fetch tenant metadata for individual rows.

The institution filter loads a bounded identity-only option model from the uplink API. The input searches institution number, short name, or full name. Submission resolves a unique option to `tenantId`; the uplink and push-monitor requests never send the display label.

## Command Flow

1. User selects a row, group, or explicit query snapshot.
2. Page computes target eligibility and completeness.
3. Confirmation displays included target count, excluded filters, limits, and required reason.
4. Submission sends an idempotent command or records why idempotency is not available.
5. Page refreshes result state and exposes success or failure.

## Failure Model

Bulk actions are disabled when loaded targets are incomplete, over limit, still loading, or failed to load. Export failures appear in the export center or the invoking page as required by the route contract.

Missing tenant metadata is a supported read state. List and monitor cells show the stable internal ID when either display name or institution number is unavailable. The detail drawer always shows the internal ID and labels missing full-name data explicitly. Tenant-option loading failure leaves numeric-ID filtering available and reports that name search is unavailable.

## Verification Model

Tests cover request payload, disabled states, target count, excluded filters, and retry behavior for each selected command.

Issue `#120` adds service tests for joined and missing tenant metadata, React tests for all display/fallback surfaces and stable-ID filter serialization, and Chrome Playwright coverage for list, detail, monitor, selection, and missing-name behavior.
