CREATE TABLE custom_report_definitions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    report_name VARCHAR(128) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    tenant_id BIGINT UNSIGNED NULL,
    role_scope ENUM('PLATFORM','TENANT') NOT NULL DEFAULT 'PLATFORM',
    dimensions_json JSON NOT NULL,
    measures_json JSON NOT NULL,
    filters_json JSON NOT NULL,
    definition_snapshot JSON NOT NULL,
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_custom_report_definition_metric (metric_code, status),
    KEY idx_custom_report_definition_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 43 custom report immutable definitions';

CREATE TABLE custom_report_export_requests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    report_definition_id BIGINT UNSIGNED NOT NULL,
    definition_snapshot JSON NOT NULL,
    status ENUM('REQUESTED','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'REQUESTED',
    requested_by VARCHAR(128) NOT NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_custom_report_export_definition (report_definition_id, requested_at),
    KEY idx_custom_report_export_status (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 43 custom report export requests without file generation';

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'custom-report:menu', '自定义报表菜单', 'MENU', '/admin/custom/reports', NULL, NULL, 610, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='custom-report:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'custom-report:read', '查看自定义报表', 'API', '/api/v1/console/custom-reports', 'GET', NULL, 611, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='custom-report:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'custom-report:write', '维护自定义报表', 'API', '/api/v1/console/custom-reports', 'POST', NULL, 612, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='custom-report:write');
