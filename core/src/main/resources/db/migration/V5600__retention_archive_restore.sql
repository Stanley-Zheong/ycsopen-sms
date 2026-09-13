CREATE TABLE archive_policies (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    data_domain VARCHAR(64) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    retention_days INT NOT NULL DEFAULT 730,
    hot_months INT NOT NULL DEFAULT 3,
    partition_unit ENUM('MONTH') NOT NULL DEFAULT 'MONTH',
    legal_hold_until DATETIME NULL,
    encryption_required TINYINT(1) NOT NULL DEFAULT 1,
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    updated_by VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_archive_policies_domain (data_domain),
    KEY idx_archive_policies_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 47 retention and hot/cold archive policy';

CREATE TABLE archive_manifests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    policy_id BIGINT UNSIGNED NOT NULL,
    data_domain VARCHAR(64) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    partition_key VARCHAR(16) NOT NULL,
    tenant_id BIGINT UNSIGNED NULL,
    archive_status ENUM('COMPLETED','FAILED','CORRUPTED','RESTORED') NOT NULL,
    row_count BIGINT NOT NULL DEFAULT 0,
    source_identity_json JSON NOT NULL,
    manifest_json JSON NOT NULL,
    archive_ciphertext LONGBLOB NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    encryption_key_version VARCHAR(32) NOT NULL DEFAULT 'archive-v1',
    retention_until DATETIME NOT NULL,
    legal_hold_until DATETIME NULL,
    deletion_eligible TINYINT(1) NOT NULL DEFAULT 0,
    failure_reason VARCHAR(255),
    created_by VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at DATETIME NULL,
    restored_at DATETIME NULL,
    exported_task_id BIGINT UNSIGNED NULL,
    KEY idx_archive_manifest_domain_partition (data_domain, partition_key),
    KEY idx_archive_manifest_status (archive_status, deletion_eligible),
    KEY idx_archive_manifest_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 47 encrypted archive manifest and checksum evidence';

CREATE TABLE archive_restore_jobs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    manifest_id BIGINT UNSIGNED NOT NULL,
    request_type ENUM('RESTORE','EXPORT') NOT NULL,
    status ENUM('RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'RUNNING',
    requested_by VARCHAR(64),
    result_message VARCHAR(255),
    restored_record_count BIGINT NOT NULL DEFAULT 0,
    export_task_id BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    KEY idx_archive_restore_manifest (manifest_id),
    KEY idx_archive_restore_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 47 restore and archive-export job evidence';

INSERT INTO archive_policies
    (data_domain, source_table, retention_days, hot_months, updated_by)
VALUES
    ('MESSAGE_TASKS', 'message_tasks', 730, 3, 'phase47'),
    ('DELIVERY_REPORTS', 'delivery_reports', 730, 3, 'phase47'),
    ('UPLINK_RECORDS', 'uplink_records', 730, 3, 'phase47'),
    ('UNSUBSCRIBE_RECORDS', 'unsubscribe_records', 730, 3, 'phase47'),
    ('BULK_SENDINGS', 'bulk_sendings', 730, 3, 'phase47'),
    ('PRIVILEGED_OPERATION_AUDITS', 'privileged_operation_audits', 730, 3, 'phase47'),
    ('COMPLAINTS', 'complaints', 730, 3, 'phase47'),
    ('BALANCE_AUDITS', 'balance_audit_entries', 730, 3, 'phase47')
ON DUPLICATE KEY UPDATE
    source_table = VALUES(source_table),
    retention_days = VALUES(retention_days),
    hot_months = VALUES(hot_months),
    updated_by = VALUES(updated_by);

INSERT INTO permissions (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('retention-archive:menu', '归档保留菜单', 'MENU', 'admin-retention-archive-nav-menu', NULL, NULL, 560, 'ACTIVE'),
    ('retention-archive:read', '查看归档策略与清单', 'API', '/api/v1/console/archive/**', 'GET', NULL, 561, 'ACTIVE'),
    ('retention-archive:write', '配置归档策略并创建归档', 'API', '/api/v1/console/archive/**', NULL, NULL, 562, 'ACTIVE'),
    ('retention-archive:verify', '校验归档清单', 'API', '/api/v1/console/archive/manifests/*/verify', 'POST', NULL, 563, 'ACTIVE'),
    ('retention-archive:restore', '恢复归档数据', 'API', '/api/v1/console/archive/manifests/*/restore', 'POST', NULL, 564, 'ACTIVE'),
    ('retention-archive:export', '导出归档数据', 'API', '/api/v1/console/archive/manifests/*/export', 'POST', NULL, 565, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    resource_type = VALUES(resource_type),
    resource_path = VALUES(resource_path),
    http_method = VALUES(http_method),
    sort_order = VALUES(sort_order),
    status = VALUES(status);
