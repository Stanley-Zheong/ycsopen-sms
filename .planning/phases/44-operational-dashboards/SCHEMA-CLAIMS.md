# Phase 44 Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
|---|---|---|---|---|---|---|---|
| P44-SCHEMA-01 | operational_dashboard_configs | operational-dashboards | V5300__operational_dashboards.sql | V1__init_schema.sql | New table only; no existing data rewrite | Drop `operational_dashboard_configs` before using Phase44 endpoints | Not required; new owner prefix |
| P44-SCHEMA-02 | permissions.operational-dashboard:* | operational-dashboards | V5300__operational_dashboards.sql | V1__init_schema.sql | Inserts permission rows with `NOT EXISTS` guards | Remove three `operational-dashboard:*` permission rows if rolling back | Follows existing permission catalog pattern |
