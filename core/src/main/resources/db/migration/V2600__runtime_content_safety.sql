CREATE TABLE content_safety_hits (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    policy_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NULL,
    template_id BIGINT UNSIGNED NULL,
    action ENUM('BLOCK','REPLACE','ALERT') NOT NULL,
    level ENUM('HIGH','MEDIUM','LOW') NOT NULL,
    matched_word VARCHAR(128) NOT NULL,
    result_content VARCHAR(500) NULL,
    blocked TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_content_safety_hit_time (created_at),
    KEY idx_content_safety_hit_policy (policy_id),
    KEY idx_content_safety_hit_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 17 runtime final-content safety hit evidence';

CREATE TABLE content_safety_export_requests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    filter_word VARCHAR(128) NULL,
    filter_category VARCHAR(32) NULL,
    filter_action VARCHAR(32) NULL,
    filter_status VARCHAR(32) NULL,
    matched_rows INT NOT NULL DEFAULT 0,
    actor VARCHAR(64) NOT NULL,
    status ENUM('REQUESTED') NOT NULL DEFAULT 'REQUESTED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_content_safety_export_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 17 content safety export request metadata';

INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('content-safety:menu', '内容审核菜单', 'MENU', '/admin/content-safety', NULL, NULL, 380, 'ACTIVE'),
    ('content-safety:read', '查看内容审核词库', 'API', '/api/v1/console/content-safety/policies', 'GET', NULL, 381, 'ACTIVE'),
    ('content-safety:write', '维护内容审核词库', 'API', '/api/v1/console/content-safety/policies', 'POST', NULL, 382, 'ACTIVE'),
    ('content-safety:import', '导入内容审核词库', 'BUTTON', 'admin-runtime-content-content-safety-import', 'POST', NULL, 383, 'ACTIVE'),
    ('content-safety:export', '请求内容审核词库导出', 'BUTTON', 'admin-runtime-content-content-safety-export', 'POST', NULL, 384, 'ACTIVE'),
    ('content-safety:scan', '执行最终内容试扫', 'API', '/api/v1/console/content-safety/scan', 'POST', NULL, 385, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
