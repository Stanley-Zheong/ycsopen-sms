# Decisions

## DR-P05-001 — Revalidate mutable authority on every console request

JWTs carry identity and session coordinates, not durable authorization truth. Account state, validity, session revocation, and permission authorities are read from the current database on every protected request so disablement and permission changes apply without waiting for token expiry.

## DR-P05-002 — Keep notification delivery outside identity

Unusual login creates a durable, deduplicated outbox handoff only. Recipient selection, channel routing, retries, and delivery reporting belong to Phase 35. This preserves an honest identity boundary without building a second notification system.

## DR-P05-003 — Treat browser fixtures as UI evidence only

Playwright route fixtures verify rendered controls, browser validation, request shape, feedback, and stable selectors. They never prove server persistence, authorization, cryptography, or transactionality. Those claims require focused Java integration/security/transaction tests and are recorded separately in `TEST-MATRIX.md` and `EVIDENCE/`.

## DR-P05-004 — Serialize role membership against role deletion

Every role assignment and deletion locks all involved platform-role rows with `SELECT ... FOR UPDATE` in ascending role ID order. This prevents a validated assignment from committing against a concurrently deleted role without introducing a separate locking subsystem.

## DR-P05-005 — Require transactional role migration

An in-use role cannot be deleted directly. User associations move to a distinct active role and the old role is removed within one transaction. An unused role still requires an explicit destructive confirmation in the UI.

## DR-P05-006 — Use only installed Google Chrome

Browser acceptance uses `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` at 1440x900. No browser binaries, drivers, Edge, Safari, Firefox, or compatibility matrix are downloaded or admitted.

## DR-P05-007 — Keep Phase 5 planning lean

One executable plan covers this coherent module. Verification is the mapped obligation/test set, production UI reconciliation, independent review, and empty TODO; no extra review rounds, release tags, or schedule artifacts are added.

## DR-P05-008 — Prevent self-edit of account identity and role membership

An administrator cannot use the platform-account mutation endpoint on their own account. This keeps user type and role assignment from becoming a self-escalation path; personal read-only data remains available through account overview.

## DR-P05-009 — Fail closed when protected phone data is unreadable

Account-list projection fails if any protected-phone envelope cannot be authenticated or decrypted. Returning a partial or silently blank identity record would hide protected-data corruption; recovery belongs to the existing protected-data operations path.

## DR-P05-010 — Normalize authorization failures by status, not response body

Both URL-matcher and method-security denials are HTTP 403, which is the client contract. The frontend does not depend on a specific 403 body, so Phase 5 does not add a second security-handler customization solely for response-shape uniformity.

## DR-P05-011 — Bound delegated identity administration by the actor's live authority

A non-ADMIN actor cannot mutate a role they currently hold, grant a permission they do not currently hold, assign or migrate an account into a role whose active permissions exceed their own, or create/promote/demote an ADMIN account. ADMIN bypass remains explicit. These checks run inside the transactional service boundary against current database state so a narrow role-management permission cannot become a self-escalation primitive.

## DR-P05-012 — Keep login presentation separate from capability completion claims

The public login shell may explain the platform's multi-tenant SMS operations purpose and use a repository-owned abstract background, but it does not enumerate roadmap items as delivered features. Authentication remains the only action on the page. The presentation reuses the Phase 2 blue/teal tokens, preserves all Phase 1 compatibility selectors, and keeps remembered-username storage limited to the username value.
