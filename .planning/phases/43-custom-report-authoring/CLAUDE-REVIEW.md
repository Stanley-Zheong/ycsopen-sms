# Phase 43 Claude Review

## Review attempt boundary

Claude diff review was attempted three ways:

1. Full staged diff against `origin/phase/42-tenant-risk-warning-auto-pause`: interrupted after no output.
2. Executable-code-only diff: interrupted after no output.
3. Read-only file consult with `Read/Grep/Glob`: interrupted after no output.

A final Claude summary review completed without file tools. Its findings were treated as risk prompts and checked against the implementation and tests below.

## Claude summary findings and disposition

### Critical: per-field metric validation ambiguous

Disposition: closed by code and tests.

Evidence:

- `CustomReportService.check()` validates every requested dimension against the metric-specific whitelist before preview/save.
- `CustomReportService.check()` validates every requested measure against the metric-specific whitelist before preview/save.
- `CustomReportServiceTest.rejectsUnsupportedDimensionsAndMeasuresBeforeQueryingAggregates` fails if unsupported fields are allowed.

### Critical: tenant actor derivation trust chain unverified

Disposition: closed by code and tests.

Evidence:

- `JwtAccessVerifier.verify()` parses verified JWT claims subject as `userId` and returns `VerifiedAccess(String.valueOf(userId), authorities)`.
- `JwtAuthenticationFilter` installs `VerifiedAccess.subject()` as `Authentication.getName()`.
- `JwtSecurityBoundaryTest.consoleBoundaryAcceptsValidTokenAndExposesAuthenticatedSubject` covers the filter principal boundary.
- `JwtAccessVerifierTest.resolvesCurrentDatabasePermissions` asserts the verified access subject is the numeric user id string.
- `CustomReportControllerTest.derivesTenantActorFromAuthenticatedUserWhenRoleIsTenantScoped` verifies non-platform roles are resolved through `UserRepository` and passed to the service as `Actor.tenant`.

### Important: metric selector reset on switch unconfirmed

Disposition: closed by frontend test.

Evidence:

- `AdminCustomReportsPage` derives dimensions/measures from the currently selected capability instead of persisting stale selections.
- `custom-report-authoring.test.tsx` switches from `CHANNEL_DELIVERY` to `TENANT_BEHAVIOR` and asserts the second preview command uses tenant dimensions, tenant measures, and `roleScope: TENANT`.

### Important: whitelist drift between capability filter and maps

Disposition: fixed.

Evidence:

- `CustomReportService.capabilities()` now derives supported metric codes from the intersection of `DIMENSIONS` and `MEASURES` instead of duplicating a hard-coded SQL list.
- `CustomReportServiceTest.capabilitiesHideActiveRegistryMetricsWithoutSupportedAuthoringContract` verifies active unmapped registry rows are not exposed.

### Important: truncation boundary off-by-one

Disposition: closed by code and test.

Evidence:

- `preview()` queries `LIMIT 501`, computes `truncated` before slicing, and returns at most 500 rows.
- `CustomReportServiceTest.previewReportsWhenRowsAreTruncatedAtFiveHundred` verifies 501 matched rows return 500 displayed rows and `truncated=true`.

### Important: measures silently truncated

Disposition: fixed.

Evidence:

- `AdminCustomReportsPage` now submits all measures from the selected capability.
- `custom-report-authoring.test.tsx` verifies both default and switched metric commands include full measure lists.

## Result

No remaining actionable Critical or Important item is open after the applied fixes and targeted verification.
