# Phase 5 TODO

Completion requires this verified list to be empty.

## Owned obligations

- [x] OBL-F-1-1-A — platform account create/edit
- [x] OBL-F-1-1-B — disablement and attributable mutation
- [x] OBL-F-1-2-A — four-granularity server-side permissions
- [x] OBL-F-1-2-B — live permission changes
- [x] OBL-F-1-2-C — in-use role migration before deletion
- [x] OBL-F-1-4-A — protected password login and lockout
- [x] OBL-F-1-4-B — timeout/logout/stale session invalidation
- [x] OBL-F-1-4-C — login history and unusual-login notification
- [x] OBL-F-1-5-A — current account/permission view
- [x] OBL-CRYPTO-PASSWORD-001 — bcrypt/Argon2 one-way password storage
- [x] OBL-FIELD-ACCOUNT-USERNAME — username validation
- [x] OBL-FIELD-ACCOUNT-PASSWORD — password validation
- [x] OBL-FIELD-ACCOUNT-PHONE — protected phone validation/storage
- [x] OBL-FIELD-ACCOUNT-TYPE — platform type validation
- [x] OBL-FIELD-ACCOUNT-VALIDITY — validity policy
- [x] OBL-STATE-ACCOUNT-LOCK — configured failure lock
- [x] OBL-STATE-ACCOUNT-UNLOCK — administrator manual unlock
- [x] OBL-STATE-ACCOUNT-DISABLE — disable and revoke
- [x] OBL-STATE-ACCOUNT-ENABLE — authorized re-enable
- [x] OBL-EDGE-INTERNAL-ERROR — safe correlated 500 UI
- [x] OBL-DATA-10-1-IDENTITY — identity/session/history persistence

## Delivery

- [x] JWT boundary rejects missing, forged, and expired tokens — Evidence: `JwtSecurityBoundaryTest`, 6/6 PASS.
- [x] Browser session rejects malformed/expired state and separates platform/tenant routes — Evidence: frontend unit suite, 16/16 PASS and build PASS.
- [x] Account field policy, password expiry, lockout, current database permission resolution, role migration, state transitions, durable session/login history, logout, and session revocation primitives are tested — Evidence: focused backend suite, 18/18 PASS.
- [x] Complete account management and login-history query APIs.
- [x] Complete database-backed current-permission enforcement and state transitions.
- [x] Complete production identity/RBAC UI and Playwright acceptance.
- [x] Run full backend/frontend suites and Phase5 review with no BLOCKER/HIGH.
- [x] Record verification/summary, empty this TODO, then commit Phase5.
