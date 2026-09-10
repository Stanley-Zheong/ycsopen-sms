ALTER TABLE complaint_ratio_stats
    ADD COLUMN data_quality VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE complaint_ratio_stats
    ADD COLUMN source_registry VARCHAR(128) NOT NULL DEFAULT 'complaint_ratio_stats:message_tasks:complaints';

CREATE INDEX idx_complaint_ratio_quality
    ON complaint_ratio_stats(stat_month, dimension_type, data_quality);

UPDATE complaint_ratio_stats
   SET data_quality = CASE
        WHEN send_count = 0 AND complaint_count > 0 THEN 'UNKNOWN'
        WHEN send_count = 0 THEN 'ZERO_DENOMINATOR'
        ELSE 'COMPLETE'
       END,
       source_registry = 'complaint_ratio_stats:message_tasks:complaints',
       threshold_config_version = COALESCE(threshold_config_version, 'default-v1');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'complaint-ratio:menu', '投诉占比看板菜单', 'MENU', '/console/dashboard/complaint-ratio', NULL, NULL, 5400, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='complaint-ratio:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'complaint-ratio:read', '投诉占比看板查看', 'API', '/api/v1/console/dashboard/complaint-ratio/**', 'GET', NULL, 5401, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='complaint-ratio:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'complaint-ratio:intervene', '投诉占比干预', 'API', '/api/v1/console/dashboard/complaint-ratio/**/pause', 'POST', NULL, 5402, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='complaint-ratio:intervene');
