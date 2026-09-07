# Iterations

## Entry baseline

- Selected exactly one owner obligation and one production route.
- Reduced the proposed storage from separate attempt/event tables to one immutable version ledger plus one singleton active-state row.
- Reduced hot reload to one local atomic snapshot. Distributed propagation and generic listener infrastructure are excluded.
- Kept one real runtime consumer boundary (`AuthService`) and one reference-only future secret setting; no referenced secret is resolved in this phase.

## Implementation log

- Entry review attempt 1 found eight blockers: missing goal-backward plan metadata, oversized plan, skipped MySQL profile, mocked browser boundary, secret-mask round trip, incomplete UI cells/permission states, ambiguous draft discard, and transaction/apply ordering.
- Revision 1 split the same phase into three bounded plans; enabled the integration profile explicitly; specified a real Spring/MySQL/Chrome harness; changed the API to server-merged changes; expanded the UI inventory to 60 selectors; limited discard to unsaved edits; and specified prepare → committed CAS → atomic apply → reload-status ordering.
- Entry review attempt 2 confirmed the original eight blockers closed, then found the HTML source was static and real-service permission/fault fixtures were underspecified.
- Revision 2 added a minimal clickable prototype state machine with action-to-state Playwright assertions. It also specifies four real database identities, two-session stale proof, offline/retry, and a clean division where service injection proves prepare rejection while Chrome renders its persisted safe status through the real API; no production fault endpoint is added.
- Implementation added the closed three-key registry, immutable version/state schema, server-owned merge, commit-before-apply runtime boundary, exact RBAC API, and one real `AuthService` consumer for both active settings.
- Full Spring startup exposed five existing constructor/proxy ambiguities that focused mocks had not exercised. The repair added explicit injection metadata and made the optional platform-notification bootstrap conditional on its SPI; no new abstraction or product behavior was introduced.
- Final review found an out-of-order apply race and missing database history enforcement. The repair made runtime snapshot application version-monotonic, added strict lifecycle/delete triggers, and proved both with deterministic threading and the runtime MySQL identity. The same review's UI/API findings were closed with visible mutation feedback, live menu permission semantics, and API documentation.
- Claude review found registry-version evolution, committed-PENDING restart, translated-message classification, applied-state atomicity, and integer parsing gaps. The repair defaults newly added registered keys into older snapshots while rejecting unknown keys, proves a real-MySQL process restart from PENDING, returns/consumes stable `data.errorCode`, wraps both applied-state writes in one transaction, and aligns client integer validation with the server. The newest-50 history boundary and the five existing bean-wiring repairs are now explicit plan/design decisions rather than hidden behavior.
