CREATE TABLE operational_dashboard_configs (
  role_code VARCHAR(32) PRIMARY KEY,
  global_cards BOOLEAN NOT NULL,
  tenant_cards BOOLEAN NOT NULL,
  refresh_mode VARCHAR(16) NOT NULL,
  polling_seconds INT NOT NULL,
  complaint_threshold VARCHAR(32) NOT NULL,
  updated_by VARCHAR(128) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO operational_dashboard_configs(role_code, global_cards, tenant_cards, refresh_mode, polling_seconds, complaint_threshold, updated_by)
VALUES
  ('ADMIN', TRUE, TRUE, 'MANUAL', 300, '0.0030', 'migration'),
  ('OPERATOR', TRUE, TRUE, 'MANUAL', 300, '0.0030', 'migration'),
  ('FINANCE', TRUE, FALSE, 'MANUAL', 300, '0.0030', 'migration'),
  ('TENANT_ADMIN', FALSE, TRUE, 'MANUAL', 300, '0.0030', 'migration'),
  ('TENANT_DEV', FALSE, TRUE, 'MANUAL', 300, '0.0030', 'migration'),
  ('TENANT_USER', FALSE, TRUE, 'MANUAL', 300, '0.0030', 'migration');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'operational-dashboard:menu', '运营仪表盘菜单', 'MENU', '/console/operational-dashboards', NULL, NULL, 5300, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='operational-dashboard:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'operational-dashboard:read', '运营仪表盘查看', 'API', '/api/v1/console/operational-dashboards/**', 'GET', NULL, 5301, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='operational-dashboard:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'operational-dashboard:write', '运营仪表盘配置', 'API', '/api/v1/console/operational-dashboards/configuration', 'POST', NULL, 5302, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='operational-dashboard:write');
