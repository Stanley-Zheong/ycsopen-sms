ALTER TABLE export_tasks
    ADD COLUMN request_id VARCHAR(64) NULL AFTER id,
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER request_id,
    ADD COLUMN producer VARCHAR(64) NOT NULL DEFAULT 'LEGACY' AFTER export_type,
    ADD COLUMN job_name VARCHAR(128) NOT NULL DEFAULT '导出任务' AFTER producer,
    ADD COLUMN authorization_snapshot JSON NULL AFTER job_name,
    ADD COLUMN source_snapshot JSON NULL AFTER authorization_snapshot,
    ADD COLUMN artifact_manifest JSON NULL AFTER source_snapshot,
    ADD COLUMN artifact_ciphertext LONGBLOB NULL AFTER artifact_manifest,
    ADD COLUMN file_sha256 CHAR(64) NULL AFTER file_size_bytes,
    ADD COLUMN encryption_state ENUM('ENCRYPTED','PASSWORD_PROTECTED') NOT NULL DEFAULT 'PASSWORD_PROTECTED' AFTER file_sha256,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER encryption_state,
    ADD COLUMN split_count INT NOT NULL DEFAULT 1 AFTER retry_count,
    ADD COLUMN partial_failure_count INT NOT NULL DEFAULT 0 AFTER split_count,
    ADD COLUMN failure_reason VARCHAR(255) NULL AFTER partial_failure_count,
    ADD COLUMN download_token_hash CHAR(64) NULL AFTER failure_reason,
    ADD COLUMN expires_at DATETIME NULL AFTER download_token_hash,
    ADD COLUMN completed_at DATETIME NULL AFTER expires_at,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER completed_at,
    ADD UNIQUE KEY uk_export_tasks_request (request_id),
    ADD KEY idx_export_tasks_center (status, created_at),
    ADD KEY idx_export_tasks_tenant (tenant_id, created_at),
    ADD KEY idx_export_tasks_type_format (export_type, file_format);

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'secure-async-export:menu', '安全异步导出中心菜单', 'MENU', '/admin/export-center', NULL, NULL, 5500, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='secure-async-export:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'secure-async-export:read', '查看安全异步导出任务', 'API', '/api/v1/console/exports', 'GET', NULL, 5501, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='secure-async-export:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'secure-async-export:create', '创建安全异步导出任务', 'API', '/api/v1/console/exports', 'POST', NULL, 5502, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='secure-async-export:create');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'secure-async-export:download', '下载安全异步导出文件', 'API', '/api/v1/console/exports/*/download', 'GET', NULL, 5503, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='secure-async-export:download');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'secure-async-export:retry', '重试失败导出任务', 'API', '/api/v1/console/exports/*/retry', 'POST', NULL, 5504, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='secure-async-export:retry');
