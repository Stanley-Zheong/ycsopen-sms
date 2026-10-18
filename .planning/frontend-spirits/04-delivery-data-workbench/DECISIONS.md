# Spirit 04 Decisions

## DR-FE04-001: Workbench Actions Are Snapshot Commands

### Status
Accepted

### Context
Message operations and exports can operate on filters, loaded targets, grouped errors, and backend limits. Prior issue `#91` showed risks around detached inputs and ambiguous bulk action targets.

### Decision
Workbench commands must disclose and lock the target snapshot before submission. Unsupported filters are disclosed and omitted instead of silently sent.

### Consequences

- Export and bulk action dialogs may need additional explanatory copy.
- Tests must assert request payload and target snapshot, not only button clicks.

## DR-FE04-002: Uplink Tenant Identity Is a Server Projection

### Status
Accepted

### Context
Issue `#120` requires operators to recognize the institution behind an uplink without translating an internal tenant ID or issuing one request per row.

### Decision
The uplink service left-joins the tenant master when reading uplink records and push events. Each row returns `tenantId`, `tenantNo`, `tenantShortName`, and `tenantFullName`; deleted or otherwise unavailable tenant metadata remains nullable so the stored `tenantId` is still visible. A lightweight tenant-option response supplies the same identifiers for the filter. The browser displays names and institution numbers but submits only the selected stable `tenantId` to uplink queries.

### Consequences

- Uplink records remain readable after tenant metadata is unavailable.
- The UI performs no row-by-row tenant lookup.
- Ambiguous free text cannot be converted silently to a tenant ID; the user must choose a unique option or enter a numeric internal ID.

## DR-FE04-003: Aggregation State Controls Numeric Meaning

### Status
Accepted

### Context
Issue `#119` shows that an empty aggregate table can mean either no business
traffic or a pipeline that never ran. Rendering both as zero is misleading.

### Decision
The platform dashboard consumes a required, server-owned `todayAggregation`
state; a missing object is an API contract failure and is never synthesized as
`NOT_REFRESHED`. It
renders em dashes and explicit guidance for `NOT_REFRESHED` and `EMPTY`.
`STALE` retains last-known values only when `aggregateRowCount` is positive; a
stale empty checkpoint also renders em dashes. `FRESH` renders normal numeric
values. Asia/Shanghai date, zone, source and freshness remain visible.

### Consequences

- Today aggregate values are nullable at the API boundary.
- Status text and values have stable independent selectors.
- Manual refresh refetches only; it does not claim to run the backend scheduler.
