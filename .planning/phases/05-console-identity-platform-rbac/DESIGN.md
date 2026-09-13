# Design

Schema migrations: declared

## Identity and session flow

`AuthController` passes username, password, remote address, and user agent to `AuthService`. Successful authentication issues a signed JWT with a unique `jti`, persists the corresponding session, and records login history. The request filter validates signature/expiry, then `JwtAccessVerifier` re-reads account state, account/password validity, durable session revocation, user type, and current permissions before constructing Spring authorities.

Logout revokes the exact durable session before the browser store is cleared. Account disablement revokes all active sessions for that account. The browser expiry timer clears protected state and routes the user back to login.

## Account administration

Platform account create/edit applies one username, password, phone, platform-type, role, and validity policy in the service layer. Passwords use BCrypt one-way hashing. Phones are encoded with the Phase 3 protected-field codec and API responses expose only a masked representation. Blank password or phone during edit means unchanged. State changes are explicit, permission-checked, self-state changes are rejected, sessions are revoked where required, and `account_change_history` records the actor and transition.

## RBAC

Roles bind permissions of type MENU, BUTTON, API, and DATA. Controllers use current database-backed authorities; frontend visibility is convenience only. Permission changes take effect on the next authenticated request because authorities are not trusted from the JWT. Deleting an in-use role requires a distinct active replacement and migrates associations in the same transaction before deletion.

## Login anomaly boundary

A successful login from a different address is recorded as unusual and writes a deduplicated `PENDING` identity-notification outbox item. Phase 5 guarantees the durable handoff; Phase 35 owns recipient/channel routing and delivery status.

## Error boundary

`CorrelationIdFilter` assigns or propagates a safe correlation identity. The global handler returns only the fixed busy message and trace identity for unexpected failures. The React root renders a dismissible alert and never shows exception, SQL, request, credential, or protected-field detail.

## UI

The production routes are `/admin/auth/login`, `/admin/system/users`, `/admin/system/roles`, `/admin/system/login-history`, and `/admin/account-overview`. Phase 2 colors, typography, spacing, shell, and desktop-only 1440x900 Chrome contract are reused. `UI-ELEMENTS.md` is the exact page/action/state selector registry.

## Rollback

V1400 and V1401 are additive. Application rollback leaves new nullable columns/tables and permission rows in place; a forward corrective migration may remove only proven-unused Phase 5 records after snapshot verification. No destructive down migration is required.
