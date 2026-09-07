-- Phase 06 current-RBAC permission catalog.
INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('audit:menu', '安全审计菜单', 'MENU', '/admin/system/logs', NULL, NULL, 200, 'ACTIVE'),
    ('audit:operations:read', '查询操作日志', 'API', '/api/v1/console/operation-audits', 'GET', NULL, 210, 'ACTIVE'),
    ('audit:operations:all', '全量操作日志数据范围', 'DATA', 'privileged-operation-audits:*', NULL, NULL, 220, 'ACTIVE'),
    ('audit:security-events:read', '查询安全事件', 'API', '/api/v1/console/security-events', 'GET', NULL, 230, 'ACTIVE'),
    ('audit:security-events:all', '全量安全事件数据范围', 'DATA', 'security-events:*', NULL, NULL, 240, 'ACTIVE'),
    ('privileged:data:reveal', '查看敏感字段按钮', 'BUTTON', 'shared-privileged-data-sensitive-value-reveal', NULL, NULL, 250, 'ACTIVE'),
    ('privileged:data:reveal:api', '查看敏感字段接口', 'API', '/api/v1/console/platform-accounts/{userId}/phone/reveal', 'POST', NULL, 260, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
