# Phase 09 Context

## Dependency evidence

- Phase 02 provides the tenant page registry, shell, tokens, routes, and
  prototype selector conventions.
- Phase 03 provides `ProtectedFieldCodec`, envelope/key references, and the
  protected-field test patterns.
- Phase 05 provides `users`, `roles`, `user_roles`, JWT identity, current
  tenant-role authorization, and disabled-account rejection.
- Phase 06 provides append-only operation audit and redaction boundaries.
- Phase 08 provides the qualification/status eligibility fence and is a hard
  prerequisite; its `SUMMARY.md`, empty `TODO.md`, and remote SHA must be
  visible before Phase 09 implementation entry is authorized.
- Phase 08 executable verification records final PASS, empty TODO, and pushed
  remote SHA `0468fe5c9fd229c43fc748b8760a8c273527f8bd`; its summary's stale
  “commit pending” wording is retained as history and is not a blocker.

## Current implementation facts

- `users.tenant_id`, `users.user_type`, `users.status`, `roles.role_type`,
  `roles.tenant_id`, and `user_roles` already support tenant account and role
  membership; no new identity/session store is needed.
- `tenant_api_keys` already contains App Key, encrypted secret, status,
  allow-list JSON, four rate columns, expiry, and last-use metadata. Its JPA
  entity currently maps only the authentication projection and rate-per-second
  field, so Phase 09 must complete the safe management mapping and add only
  the missing description/revocation metadata.
- `tenant_protocol_credentials` already contains encrypted account/password,
  protocol, allow-list, max connections, TPS, window, and status. The existing
  schema lacks explicit endpoint/SPID/revocation metadata required by this
  phase.
- `HmacAuthInterceptor` intentionally does not yet verify request bodies or
  enforce API-key policy. Phase 09 must not expand into that out-of-scope
  protocol/request path; it must ensure the existing lookup observes status.
- `TenantLayout` currently routes configuration to a placeholder and has no
  administrator/API-key/CMPP production pages. Phase 09 owns those three
  documented routes and their role-aware navigation.

## Locked decisions

- D-09-001: Chrome-only desktop acceptance uses the installed local Google
  Chrome at 1440x900; no Chrome for Testing download, mobile, or browser matrix.
- D-09-002: Reuse Phase 05 identity/RBAC, JWT, and tenant binding; never trust a
  tenant ID supplied by the browser or create a second auth system.
- D-09-003: Use Phase 03 protected-field encryption for API/CMPP secrets;
  create responses are controlled one-time handoffs and all later reads are
  masked/no-secret.
- D-09-004: Keep revocation propagation to the persisted status plus one
  synchronous domain event; do not add an event bus or workflow engine.
- D-09-005: Keep HTTP signature verification and CMPP protocol sessions out of
  this phase, per the roadmap boundary.

## Scope guard

No mobile implementation, cross-browser matrix, request-signature gateway,
CMPP session server, credential export/recovery, or generalized credential
service is part of Phase 09.
