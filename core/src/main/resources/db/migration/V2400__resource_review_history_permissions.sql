INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('review-history:menu', '统一审核历史菜单', 'MENU', '/admin/review-history', NULL, NULL, 350, 'ACTIVE'),
    ('review-history:read', '查看统一审核历史', 'API', '/api/v1/console/review-history', 'GET', NULL, 360, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    resource_type = VALUES(resource_type),
    resource_path = VALUES(resource_path),
    http_method = VALUES(http_method),
    sort_order = VALUES(sort_order),
    status = VALUES(status);
