# Phase 41 Context

Package: `complaint-case-management`

This phase implements complaint source intake, nullable attribution quality, complaint state transitions, remediation/recovery records, and basic complaint distribution analytics.

Boundaries:

- In scope: `/admin/complaints`, `/admin/complaint/analytics`, `/api/v1/console/complaints`, `/api/v1/console/complaint-analytics`, complaint/remediation persistence.
- Out of scope: automatic complaint-ratio tenant policy, dashboard intervention thresholds, and cross-browser certification beyond local Chrome.

Dependency evidence used:

- Existing auth shell and platform admin layout.
- Existing blacklist risk-control service for exact mobile remediation.
- Existing signature/template/channel lifecycle states for resource disablement.
- Existing Phase 2 admin layout style tokens reused through current shared CSS.
