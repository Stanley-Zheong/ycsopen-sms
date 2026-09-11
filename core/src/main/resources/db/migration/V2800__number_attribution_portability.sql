CREATE TABLE number_prefix_versions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    version_no VARCHAR(32) NOT NULL,
    update_type ENUM('FULL','INCREMENTAL') NOT NULL,
    status ENUM('ACTIVE','SUPERSEDED','FAILED') NOT NULL DEFAULT 'ACTIVE',
    source_name VARCHAR(64) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    conflict_count INT NOT NULL DEFAULT 0,
    actor VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    activated_at DATETIME(6) NULL,
    UNIQUE KEY uk_number_prefix_version_no (version_no),
    KEY idx_number_prefix_versions_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 19 number prefix import/version evidence';

CREATE TABLE number_prefix_mappings (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    version_id BIGINT UNSIGNED NOT NULL,
    prefix VARCHAR(7) NOT NULL,
    carrier ENUM('MOBILE','UNICOM','TELECOM','VIRTUAL','INTERNATIONAL','UNKNOWN') NOT NULL,
    province VARCHAR(32) NOT NULL,
    city VARCHAR(32) NOT NULL,
    source_name VARCHAR(64) NOT NULL,
    status ENUM('ACTIVE','SUPERSEDED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_number_prefix_version_prefix (version_id, prefix),
    KEY idx_number_prefix_lookup (status, prefix),
    CONSTRAINT fk_number_prefix_version FOREIGN KEY (version_id) REFERENCES number_prefix_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 19 validated 3-to-7 digit prefix mappings';

-- V1 already owns this table. Evolve it in place so existing portability rows
-- survive the Phase 19 cache metadata upgrade.
ALTER TABLE mobile_portability
    MODIFY mobile_encrypted VARBINARY(255) NULL COMMENT '🔒',
    CHANGE COLUMN original_operator original_carrier
        ENUM('MOBILE','UNICOM','TELECOM','VIRTUAL','INTERNATIONAL','UNKNOWN') NOT NULL,
    CHANGE COLUMN current_operator current_carrier
        ENUM('MOBILE','UNICOM','TELECOM','VIRTUAL','INTERNATIONAL','UNKNOWN') NOT NULL,
    ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT FIRST,
    ADD COLUMN masked_mobile VARCHAR(32) NOT NULL DEFAULT '***' AFTER mobile_hash,
    ADD COLUMN source_name VARCHAR(64) NOT NULL DEFAULT 'LEGACY' AFTER ported_at,
    ADD COLUMN freshness_expires_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER source_name,
    ADD COLUMN status ENUM('ACTIVE','EXPIRED','DISABLED') NOT NULL DEFAULT 'ACTIVE' AFTER freshness_expires_at,
    ADD UNIQUE KEY uk_mobile_portability_id (id),
    ADD KEY idx_mobile_portability_freshness (status, freshness_expires_at);

CREATE TABLE portability_provider_configs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    provider_name VARCHAR(64) NOT NULL,
    freshness_seconds INT NOT NULL DEFAULT 86400,
    fallback_policy ENUM('PREFIX_ONLY','CACHE_THEN_PREFIX') NOT NULL DEFAULT 'CACHE_THEN_PREFIX',
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    actor VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_portability_provider_name (provider_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 19 portability provider/cache policy';

INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('number-attribution:menu', '号码归属菜单', 'MENU', '/admin/number-attribution', NULL, NULL, 410, 'ACTIVE'),
    ('number-attribution:read', '查看号码归属', 'API', '/api/v1/console/number-attribution', 'GET', NULL, 411, 'ACTIVE'),
    ('number-attribution:write', '维护号段归属', 'API', '/api/v1/console/number-attribution', 'POST', NULL, 412, 'ACTIVE'),
    ('number-attribution:import', '导入号段归属', 'BUTTON', 'admin-prefixes-import', 'POST', NULL, 413, 'ACTIVE'),
    ('number-attribution:portability', '维护携号转网', 'API', '/api/v1/console/number-attribution/portability', 'POST', NULL, 414, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
