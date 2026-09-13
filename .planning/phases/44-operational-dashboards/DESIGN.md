# Phase 44 Design

## Backend

- `OperationalDashboardService`
  - Builds platform realtime/KPI dashboards from `users`, `tenants`, `channels`, `fee_warning_episodes`, `statistics_metric_registry`, and `statistics_aggregates`.
  - Builds tenant overview from tenant-scoped actor plus `prepaid_accounts`, `trial_accounts`, `tenant_contracts`, and `statistics_aggregates`.
  - Builds resource/channel statistics from Phase 34 aggregate rows.
  - Builds API status rows from live source/freshness checks.
  - Clamps tenant dashboard configuration so tenant roles cannot expose global cards.

- `OperationalDashboardController`
  - `GET /api/v1/console/operational-dashboards/platform`
  - `GET /api/v1/console/operational-dashboards/tenant-overview`
  - `GET /api/v1/console/operational-dashboards/resource-statistics`
  - `GET /api/v1/console/operational-dashboards/api-status`
  - `GET /api/v1/console/operational-dashboards/configuration`
  - `POST /api/v1/console/operational-dashboards/configuration`

## Frontend

- Existing `/admin/dashboard` now renders realtime, KPI, channel health, fee warning, trend/rank tables, and existing complaint-ratio panels.
- New admin pages:
  - `/admin/statistics/resources`
  - `/admin/api/status`
  - `/admin/dashboard/configuration`
- Tenant pages:
  - `/tenant/overview` gains source-backed operational overview.
  - `/tenant/templates/statistics` shows tenant-scoped signature/template statistics.

## Data model

- `operational_dashboard_configs` stores role visibility, refresh mode, polling seconds, complaint threshold, actor, and update timestamp.
