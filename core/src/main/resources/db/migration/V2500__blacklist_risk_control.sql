ALTER TABLE blacklist_entries
    ADD COLUMN masked_mobile VARCHAR(32) NOT NULL DEFAULT '***',
    ADD COLUMN effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN expires_at DATETIME(6) NULL;

CREATE TABLE risk_provider_configs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    provider_name VARCHAR(64) NOT NULL,
    provider_url VARCHAR(255) NOT NULL,
    credential_ref VARCHAR(128) NOT NULL,
    check_level ENUM('BASIC','INTERMEDIATE','ADVANCED') NOT NULL DEFAULT 'BASIC',
    threshold_score TINYINT NOT NULL DEFAULT 85,
    timeout_ms INT NOT NULL DEFAULT 800,
    fallback_policy ENUM('ALLOW','CACHE') NOT NULL DEFAULT 'ALLOW',
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    cache_ttl_seconds INT NOT NULL DEFAULT 300,
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_risk_provider_name (provider_name),
    KEY idx_risk_provider_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 16 third-party risk provider configuration';

CREATE TABLE risk_intercept_decisions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    mobile_ref VARCHAR(128) NOT NULL,
    source_category ENUM('WHITELIST','SYSTEM_BLACKLIST','TENANT_BLACKLIST','THIRD_PARTY_RISK','THIRD_PARTY_DEGRADED','NO_MATCH') NOT NULL,
    risk_result ENUM('ALLOW','BLOCK','DEGRADED_ALLOW','DEGRADED_CACHE') NOT NULL,
    trace_reason VARCHAR(255) NOT NULL,
    task_created TINYINT(1) NOT NULL DEFAULT 0,
    charged TINYINT(1) NOT NULL DEFAULT 0,
    actor VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_risk_decision_tenant_time (tenant_id, created_at),
    KEY idx_risk_decision_source_time (source_category, created_at),
    UNIQUE KEY uk_risk_decision_request_mobile (request_id, mobile_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 16 pre-task risk decision evidence';

CREATE TABLE risk_intercept_appeals (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    decision_id BIGINT UNSIGNED NOT NULL,
    original_result VARCHAR(32) NOT NULL,
    appeal_result ENUM('PENDING','FALSE_POSITIVE','CONFIRMED_RISK') NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(255) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_risk_appeal_decision (decision_id),
    CONSTRAINT fk_risk_appeal_decision FOREIGN KEY (decision_id) REFERENCES risk_intercept_decisions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 16 immutable risk decision appeal evidence';

ALTER TABLE third_party_risk_check_logs
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL,
    ADD COLUMN provider_name VARCHAR(64) NULL,
    ADD COLUMN request_kind ENUM('SINGLE','BATCH') NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN item_count INT NOT NULL DEFAULT 1,
    ADD COLUMN risk_score TINYINT NULL,
    ADD COLUMN risk_result VARCHAR(32) NULL,
    ADD COLUMN fallback_policy VARCHAR(16) NULL,
    ADD COLUMN reason VARCHAR(255) NULL;

INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('blacklist:menu', '黑白名单菜单', 'MENU', '/admin/black-white-lists', NULL, NULL, 370, 'ACTIVE'),
    ('blacklist:read', '查看黑白名单', 'API', '/api/v1/console/risk/blacklist', 'GET', NULL, 371, 'ACTIVE'),
    ('blacklist:write', '维护黑白名单', 'API', '/api/v1/console/risk/blacklist', 'POST', NULL, 372, 'ACTIVE'),
    ('blacklist:import', '导入黑白名单', 'BUTTON', 'admin-blacklist-risk-black-white-lists-import', 'POST', NULL, 373, 'ACTIVE'),
    ('blacklist:export', '请求黑白名单导出', 'BUTTON', 'admin-blacklist-risk-black-white-lists-export', 'POST', NULL, 374, 'ACTIVE'),
    ('risk-provider:read', '查看风控服务配置', 'API', '/api/v1/console/risk/provider', 'GET', NULL, 375, 'ACTIVE'),
    ('risk-provider:write', '维护风控服务配置', 'API', '/api/v1/console/risk/provider', 'POST', NULL, 376, 'ACTIVE'),
    ('risk-analysis:read', '查看拦截分析', 'API', '/api/v1/console/risk/analytics', 'GET', NULL, 377, 'ACTIVE'),
    ('risk-analysis:check', '执行风控试算', 'API', '/api/v1/console/risk/check', 'POST', NULL, 378, 'ACTIVE'),
    ('risk-analysis:appeal', '标记误判申诉', 'BUTTON', 'admin-blacklist-risk-intercept-appeal', 'POST', NULL, 379, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
