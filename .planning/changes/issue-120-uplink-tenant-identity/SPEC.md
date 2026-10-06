# Issue 120 Uplink Tenant Identity

GitHub issue `#120` amends the completed Phase 32 uplink workbench. Operators currently see only `tenantId` in the uplink list, detail drawer, and push-monitor table.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| issue-120-uplink-tenant-identity | Admin uplink list and push-monitor rows show institution short name and institution number. The detail drawer also shows full name and stable internal tenant ID. Each list API returns these display fields without per-row requests. | Service tests prove joined and missing tenant metadata. React and Chrome show the projected identity on all three surfaces and a meaningful `tenantId` fallback. |
| issue-120-uplink-tenant-filter | An operator can find an institution by short name, full name, or institution number, while uplink requests continue to use `tenantId`. | React and Chrome select a display label and observe `tenantId=7` in both list and push-monitor requests; ambiguous nonnumeric text is not submitted. |

## Scope

- `GET /api/v1/console/uplinks`, `GET /api/v1/console/uplinks/{id}`, and `GET /api/v1/console/uplinks/push-monitor` tenant identity fields.
- Read-only tenant option lookup for ADMIN and OPERATOR uplink filters.
- `/admin/uplink` list, detail, push monitor, filter states, unit tests, and Chrome Playwright.
- Backend/API documentation and frontend spirit 04 records.

## Out of Scope

- Tenant portal presentation changes.
- Tenant master editing, deletion policy, or lifecycle semantics.
- Schema migrations or copied tenant identity snapshots in uplink tables.
- Changes to uplink replay, pause, resume, webhook delivery, or phone protection behavior.
