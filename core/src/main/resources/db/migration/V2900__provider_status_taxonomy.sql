CREATE TABLE provider_status_versions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    version_no VARCHAR(32) NOT NULL,
    status ENUM('ACTIVE','SUPERSEDED','FAILED') NOT NULL DEFAULT 'ACTIVE',
    source_name VARCHAR(64) NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    conflict_count INT NOT NULL DEFAULT 0,
    actor VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_provider_status_version_no (version_no),
    KEY idx_provider_status_versions_effective (status, effective_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 20 provider status taxonomy versions';

CREATE TABLE provider_status_mappings (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    version_id BIGINT UNSIGNED NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    platform_category ENUM('SUCCESS','FAILURE','PENDING','UNKNOWN_REVIEW_REQUIRED') NOT NULL,
    final_state TINYINT(1) NOT NULL,
    billable TINYINT(1) NOT NULL,
    retryable TINYINT(1) NOT NULL,
    severity ENUM('INFO','WARN','ERROR','CRITICAL') NOT NULL,
    advice VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE','SUPERSEDED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_provider_status_code_version (version_id, provider_name, protocol, provider_code),
    KEY idx_provider_status_lookup (status, provider_name, protocol, provider_code),
    CONSTRAINT fk_provider_status_version FOREIGN KEY (version_id) REFERENCES provider_status_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 20 effective provider-to-platform code mappings';

CREATE TABLE provider_status_normalization_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    provider_name VARCHAR(64) NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    version_no VARCHAR(32) NULL,
    platform_category VARCHAR(64) NOT NULL,
    final_state TINYINT(1) NOT NULL,
    billable TINYINT(1) NOT NULL,
    retryable TINYINT(1) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    advice VARCHAR(255) NOT NULL,
    source ENUM('MAPPED','UNKNOWN_SAFE_FALLBACK') NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_provider_status_events_code (provider_name, protocol, provider_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 20 immutable normalized status evidence';

CREATE TABLE provider_status_export_requests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    provider_name VARCHAR(64) NULL,
    protocol VARCHAR(16) NULL,
    matched_rows INT NOT NULL,
    actor VARCHAR(64) NOT NULL,
    status ENUM('REQUESTED','FAILED','READY') NOT NULL DEFAULT 'REQUESTED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_provider_status_export_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 20 status mapping export requests';

INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('provider-status:menu', '状态码映射菜单', 'MENU', '/admin/status-codes', NULL, NULL, 430, 'ACTIVE'),
    ('provider-status:read', '查看状态码映射', 'API', '/api/v1/console/provider-status', 'GET', NULL, 431, 'ACTIVE'),
    ('provider-status:write', '维护状态码映射', 'API', '/api/v1/console/provider-status', 'POST', NULL, 432, 'ACTIVE'),
    ('provider-status:import', '导入状态码映射', 'BUTTON', 'admin-provider-status-taxonomy-status-codes-import', 'POST', NULL, 433, 'ACTIVE'),
    ('provider-status:export', '请求状态码导出', 'BUTTON', 'admin-provider-status-taxonomy-status-codes-export', 'POST', NULL, 434, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
