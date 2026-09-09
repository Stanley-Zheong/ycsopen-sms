UPDATE exempt_rules SET scope = '*' WHERE scope IS NULL OR scope = '';
UPDATE exempt_rules SET valid_until = '9999-12-31 23:59:59' WHERE valid_until IS NULL;

ALTER TABLE exempt_rules
    MODIFY scope VARCHAR(255) NOT NULL DEFAULT '*',
    MODIFY valid_until DATETIME NOT NULL,
    ADD COLUMN resource_id VARCHAR(128) NOT NULL DEFAULT '*',
    ADD COLUMN product_code VARCHAR(64) NOT NULL DEFAULT 'SMS',
    ADD COLUMN approval_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN valid_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN revoked_at DATETIME NULL,
    ADD COLUMN revoked_by VARCHAR(64) NULL,
    ADD COLUMN revoke_reason VARCHAR(255) NULL,
    ADD COLUMN version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN reason VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

UPDATE exempt_rules rule_row
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, exempt_type, resource_id, product_code, scope
               ORDER BY created_at, id
           ) AS deterministic_version
    FROM exempt_rules
) ranked ON ranked.id = rule_row.id
SET rule_row.version_no = ranked.deterministic_version;

ALTER TABLE exempt_rules
    ADD UNIQUE KEY uk_exempt_rules_version (tenant_id, exempt_type, resource_id, product_code, scope, version_no),
    ADD KEY idx_exempt_rules_effective (tenant_id, exempt_type, resource_id, product_code, scope, approval_status, valid_until),
    ADD KEY idx_exempt_rules_revoked (revoked_at);

INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('exemption:menu', '豁免策略菜单', 'MENU', '/admin/exemption/policy', NULL, NULL, 310, 'ACTIVE'),
    ('exemption:read', '查看豁免策略', 'API', '/api/v1/console/exemptions', 'GET', NULL, 320, 'ACTIVE'),
    ('exemption:write', '配置与撤销豁免策略', 'BUTTON', 'admin-auditable-exemption-exemption-policy-save', 'POST', NULL, 330, 'ACTIVE'),
    ('exemption:audit', '查看豁免使用审计', 'API', '/api/v1/console/exemptions/usage-history', 'GET', NULL, 340, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);

CREATE TABLE exempt_rule_history (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    exempt_rule_id BIGINT UNSIGNED NOT NULL,
    version_no INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    scope_expression VARCHAR(255) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    result VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_exempt_rule_history_rule (exempt_rule_id),
    KEY idx_exempt_rule_history_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 14 exemption decision audit history';

CREATE TABLE exempt_rule_usage_history (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    exempt_rule_id BIGINT UNSIGNED NULL,
    version_no INT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    scope_expression VARCHAR(255) NOT NULL,
    control_code VARCHAR(64) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    result VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_exempt_usage_rule (exempt_rule_id),
    KEY idx_exempt_usage_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 14 exemption effective-use audit history';
