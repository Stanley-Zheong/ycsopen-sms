# Iterations

## Entry baseline

- The authoritative owner query returns 21 obligations.
- Two independent explorers reduced the UI to three routes and the backend to four vertical slices.
- Existing crypto/object/session, platform-message, JWT/RBAC, audit, account-status, and real-service harness foundations are reused.
- Proposed generic workflows, duplicate operating state, separate detail routes, fake future APIs, cross-browser/mobile work, and repeated Phase 03 fault matrices were removed before planning.

## Implementation and verification

- Registration, contact verification, protected evidence, review, initial access, trial state, maintenance, recertification, account status, immutable history, and the shared new-work fence are implemented as one tenant-focused vertical slice.
- Production UI is limited to `/tenant/register`, `/tenant/qualification`, and `/admin/tenants`, with 105 selectors reconciled against the documented inventory.
- Installed Chrome acceptance passed all 18 direct UI blocks. Backend policy, real MySQL lifecycle/concurrency, frontend unit/build, PRD trace, and production UI validators pass.
- Added the three previously missing evidence records for `OBL-F-2-2-C`, `OBL-F-2-4-B`, and `OBL-DATA-10-2-TENANT`; no implementation expansion was needed.
