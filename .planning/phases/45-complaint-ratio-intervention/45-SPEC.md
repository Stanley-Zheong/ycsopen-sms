# Phase 45 SPEC

Package: `complaint-ratio-intervention`

Goal: complaint ratios reconcile to source send/complaint counts by month and dimension, with explicit zero/unknown behavior and exact intervention evidence.

Scope:

- Channel and tenant monthly complaint-ratio rankings from `complaint_ratio_stats`.
- Ratio semantics: `complaint_count / send_count`; zero denominator and inconsistent complaint-only data are not actionable.
- Versioned threshold display using default `0.003` ratio (`3.00‰`) and persisted `threshold_config_version`.
- Historical month selection, Top 10/all toggle, source freshness display.
- Drill-down to exact dimension/month complaint cases.
- Authorized channel pause and tenant pause/alert evidence for complete threshold breaches.

Out of scope:

- Complaint intake workflow changes.
- Channel recovery candidate switching internals beyond existing pause evidence.
- New notification transport or new alert engine.
