-- Phase 18: frequency rules and API rate-limit evidence.

UPDATE frequency_rules SET scope='GLOBAL' WHERE scope IS NULL OR scope='';

ALTER TABLE frequency_rules
    MODIFY scope ENUM('GLOBAL','TENANT','API_KEY') NOT NULL DEFAULT 'GLOBAL',
    ADD COLUMN scope_ref_id BIGINT UNSIGNED NULL AFTER scope,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD KEY idx_frequency_scope (scope, scope_ref_id, status),
    ADD KEY idx_frequency_type_status (limit_type, status);

CREATE TABLE frequency_rule_hits (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NULL,
    api_key_id BIGINT UNSIGNED NULL,
    limit_type ENUM('MOBILE','TENANT_LEVEL','IP','CONTENT_SIMILARITY') NOT NULL,
    dimension_value VARCHAR(255) NOT NULL,
    action ENUM('BLOCK','DELAY','ALERT') NOT NULL,
    exceeded_count BIGINT NOT NULL,
    window_seconds INT NOT NULL,
    blocked TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_frequency_rule_hits_rule (rule_id, created_at),
    KEY idx_frequency_rule_hits_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.6 频控命中证据';

CREATE TABLE frequency_rule_export_requests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    filter_name VARCHAR(100) NULL,
    filter_type VARCHAR(32) NULL,
    filter_action VARCHAR(16) NULL,
    filter_status VARCHAR(16) NULL,
    matched_rows INT NOT NULL,
    actor VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.6 频控规则导出请求';

CREATE TABLE frequency_rule_exemptions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    api_key_id BIGINT UNSIGNED NULL,
    limit_type ENUM('MOBILE','TENANT_LEVEL','IP','CONTENT_SIMILARITY') NOT NULL,
    dimension_value VARCHAR(255) NULL,
    reason VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_frequency_exemption_scope (tenant_id, api_key_id, limit_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-5.6 频控豁免';

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, status)
SELECT permission_code, permission_name, resource_type, resource_path, http_method, 'ACTIVE'
FROM (
    SELECT 'frequency:menu' permission_code, '频控规则菜单' permission_name, 'MENU' resource_type, '/admin/frequency/rules' resource_path, NULL http_method
    UNION ALL SELECT 'frequency:read', '查看频控规则', 'API', '/api/frequency/rules', 'GET'
    UNION ALL SELECT 'frequency:write', '维护频控规则', 'API', '/api/frequency/rules', 'POST'
    UNION ALL SELECT 'frequency:import', '导入频控规则', 'BUTTON', '/admin/frequency/rules/import', NULL
    UNION ALL SELECT 'frequency:export', '导出频控规则', 'BUTTON', '/admin/frequency/rules/export', NULL
    UNION ALL SELECT 'frequency:scan', '试算频控规则', 'API', '/api/frequency/rules/scan', 'POST'
) desired
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.permission_code = desired.permission_code);
