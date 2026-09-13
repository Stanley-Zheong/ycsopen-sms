# Phase 44 UI Spec

## Routes

- `/admin/dashboard`
- `/admin/dashboard/configuration`
- `/admin/api/status`
- `/admin/statistics/resources`
- `/tenant/overview`
- `/tenant/templates/statistics`

## Role and permission model

- Platform dashboard pages require `operational-dashboard:menu` and `operational-dashboard:read`.
- Configuration save uses `operational-dashboard:write`.
- Tenant overview and tenant template statistics are tenant-scoped by server actor derivation.

## Required page and element IDs

- `tenant-operational-dashboards-tenant-overview-page`
- `tenant-operational-dashboards-tenant-overview-scope`
- `tenant-operational-dashboards-templates-statistics-page`
- `admin-operational-dashboards-channel-statistics-comparison`
- `admin-operational-dashboards-dashboard-finance-warning-card`
- `admin-operational-dashboards-statistics-channel-period-compare`
- `admin-operational-dashboards-statistics-resources-page`
- `admin-operational-dashboards-dashboard-realtime-page`
- `admin-operational-dashboards-dashboard-realtime-send-trend`
- `admin-operational-dashboards-dashboard-realtime-refresh`
- `admin-operational-dashboards-dashboard-kpi-page`
- `admin-operational-dashboards-dashboard-kpi-hourly-trend`
- `admin-operational-dashboards-dashboard-kpi-tenant-rank`
- `admin-operational-dashboards-dashboard-kpi-channel-health`
- `admin-operational-dashboards-dashboard-configuration-page`
- `admin-operational-dashboards-dashboard-configuration-role`
- `admin-operational-dashboards-api-status-monitor-page`
- `shared-operational-dashboards-metric-source`

## States

- Loading: page shows query loading text or refresh button disabled by React Query pending state.
- Data: cards/tables render source-backed metrics.
- Empty: resource statistics returns `empty=true` and no rows.
- Error: API query errors render page error state.
- Freshness/source: every dashboard surface exposes source, formula, freshness, permission scope, and accessible table path.
