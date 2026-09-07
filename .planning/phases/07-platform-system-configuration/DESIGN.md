# Design

Schema migrations: declared

## Typed registry

`PlatformConfigurationRegistry` owns the only accepted keys and their display label, type, default, sensitivity, and validator:

- `security.login.max-failures`: integer, default `5`, inclusive range `3..20`; consumed by `AuthService`.
- `security.login.unusual-ip-enabled`: boolean, default `true`; consumed by `AuthService`. Turning it off skips unusual-IP comparison/event creation on subsequent successful logins without changing login history or existing events; turning it on affects only subsequent logins.
- `security.export.signing-key-ref`: secret reference, default `env:YCS_SMS_EXPORT_SIGNING_KEY`; only the reference is stored and its response value is masked. A later export phase may resolve it; Phase 07 never reads the referenced secret.

The stage API accepts only a `changes` map. The server loads its complete current snapshot, applies and validates those changes, and persists the resulting complete canonical snapshot. Unknown, malformed, out-of-range, or raw secret-like values return a stable validation error before persistence or runtime mutation. GET returns secret metadata as `configured=true` plus the fixed display value `env:••••••`; this mask is never accepted as input. Omitting a secret key preserves the server-side reference, while changing it requires a new valid `env:UPPER_SNAKE_CASE` reference. Tests prove that changing only a non-sensitive key preserves the real secret reference without returning it to the client.

Registry evolution is append-only by default: when a newer binary introduces a key, an older immutable snapshot receives that key's registered default during read, reload, stage, and rollback, and the effective checksum is calculated from the normalized snapshot. Unknown persisted keys still fail closed. Removing or renaming a key requires an explicit compatibility migration before the registry changes; it is never treated as a silent defaulting operation.

## Persistence and concurrency

V1600 creates `platform_configuration_versions` and singleton `platform_configuration_state`. Each version stores a complete canonical JSON snapshot, changed-key names, checksum, actor, reason, status, and lifecycle timestamps/error code. Values are immutable after insertion except for the controlled status/lifecycle transition. The state row contains the active version and a monotonic lock version.

Staging requires `expectedActiveVersion`; the service compares it with the state row and inserts `DRAFT` only when current. A later stage marks an older pending draft `ABANDONED` in the same attributable transaction so there is one activatable draft. The UI “放弃修改” action only clears unsaved client edits and is disabled after staging; it never claims to delete a persisted draft. Activation prepares and validates an immutable runtime snapshot, then updates the singleton row with compare-and-set semantics. A stale competing operation returns HTTP 409 and cannot change the active database or runtime version. Rollback copies a historical snapshot into a new version and activates that new version; history is never rewritten.

## Safe reload

`PlatformConfigurationRuntime` holds one `AtomicReference` to an immutable validated snapshot. `prepare` can reject a snapshot without mutation; `apply` is a version-monotonic atomic reference update with no fallible work, so a delayed older apply cannot overwrite a newer version. `PlatformConfigurationService` uses `TransactionTemplate`: it prepares first, executes version/state compare-and-set with reload state `PENDING`, and calls `apply` only after `TransactionTemplate.execute` returns successfully (therefore after commit). It then records version and singleton-state `APPLIED` status atomically in one follow-up transaction. Prepare rejection creates/marks safe `RELOAD_REJECTED` history without changing the active state/runtime. CAS or commit failure never reaches `apply`. A crash after commit but before/following apply leaves `PENDING`, not a false success; startup always rehydrates and validates the database active version before serving requests and atomically resolves matching version/state status to `APPLIED`. Tests inject prepare rejection, CAS failure, transaction failure, out-of-order apply, and a real-MySQL committed-`PENDING` restart to prove ordering and recovery. API mutation errors expose a stable machine code in `data.errorCode`, so clients never classify concurrency or reload outcomes from translated display text.

## Authorization and audit

V1601 adds exact menu/read/write/activate permissions. The Admin navigation requires both `system:configuration:menu` and `system:configuration:read`; direct GET requires `system:configuration:read`; staging requires `system:configuration:write`; activation and rollback require `system:configuration:activate`. Version records retain actor, reason, changed keys, status, and safe error code. Phase 06 independently captures structural request audit. Neither store receives tokens, passwords, raw secrets, or resolved secret values.

## API

- `GET /api/v1/console/system-configuration` returns registry metadata, masked active values, the newest 50 immutable history summaries, active version, and reload status. This bounded operational view is the explicit Phase 07 boundary; full historical pagination is deferred to a history/reporting owner.
- `POST /api/v1/console/system-configuration/versions` stages a validated partial change set with expected active version and reason; the server creates the complete snapshot.
- `POST /api/v1/console/system-configuration/versions/{versionId}/activate` activates a draft with expected active version and reason.
- `POST /api/v1/console/system-configuration/versions/{versionId}/rollback` creates and activates a new version copied from history with expected active version and reason.

## UI

The `/admin/system/configuration` page uses the existing desktop Admin shell. It shows active version/reload state, typed settings, a draft summary, immutable history, and explicit edit/activate/rollback dialogs. Every page element, state, and action is listed in `UI-ELEMENTS.md` with a literal `data-testid`.

## Failure semantics

- Validation: HTTP 400 with per-key safe feedback; nothing staged or applied.
- Missing permission: HTTP 403; no configuration data or mutation.
- Stale expected version: HTTP 409 with `data.errorCode=STALE_VERSION`; refresh action retains no unsafe pending mutation.
- Reload preparation rejection: HTTP 409 with `data.errorCode=RELOAD_REJECTED`; the draft remains `RELOAD_REJECTED`, active state/runtime remain unchanged, and the safe error code is visible in history.
- Other configuration mutation failures retain the administrator's local edits and render a visible safe error instead of failing silently.

## Real-service acceptance

`Phase07RealServicePlaywrightTest` starts the disposable real-MySQL harness, runs Flyway through V1601, starts the real Spring application on an isolated loopback port, and seeds four synthetic database identities: an administrator, a no-read account, a read-only account, and a read/write account without activation permission. It launches the Phase 07 Playwright file with a Vite proxy pointed at that backend. The Playwright test uses the real login, permissions, configuration APIs, transaction/runtime service, and database; it defines no `page.route` or other network substitution. The harness captures the installed-Chrome JSON result and always stops the application and disposable database.
