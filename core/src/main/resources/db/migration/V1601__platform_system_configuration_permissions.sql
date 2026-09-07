-- Phase 07 current-RBAC permissions for the system configuration surface.
INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('system:configuration:menu', '系统配置菜单', 'MENU', '/admin/system/configuration', NULL, NULL, 270, 'ACTIVE'),
    ('system:configuration:read', '查看系统配置', 'API', '/api/v1/console/system-configuration', 'GET', NULL, 280, 'ACTIVE'),
    ('system:configuration:write', '编辑与保存系统配置', 'BUTTON', 'admin-platform-system-configuration-edit-open', 'POST', NULL, 290, 'ACTIVE'),
    ('system:configuration:activate', '激活与回滚系统配置', 'BUTTON', 'admin-platform-system-configuration-activate-open', 'POST', NULL, 300, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
