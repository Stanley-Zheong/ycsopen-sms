-- Phase 5 identity permissions; namespace SCHEMA-P05 / V1400-V1499.
INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('identity:menu', '账号与权限菜单', 'MENU', '/admin/system', NULL, NULL, 10, 'ACTIVE'),
    ('identity:accounts:read', '查看平台账号', 'API', '/api/v1/console/platform-accounts', 'GET', NULL, 20, 'ACTIVE'),
    ('identity:accounts:create', '创建平台账号', 'BUTTON', 'admin-console-identity-users-create', NULL, NULL, 30, 'ACTIVE'),
    ('identity:accounts:update', '编辑平台账号', 'BUTTON', 'admin-console-identity-users-edit', NULL, NULL, 40, 'ACTIVE'),
    ('identity:accounts:all', '平台账号全量数据范围', 'DATA', 'platform-accounts:*', NULL, NULL, 50, 'ACTIVE'),
    ('identity:roles:read', '查看角色权限', 'API', '/api/v1/console/platform-roles', 'GET', NULL, 60, 'ACTIVE'),
    ('identity:roles:create', '创建角色', 'BUTTON', 'admin-console-identity-roles-create', NULL, NULL, 70, 'ACTIVE'),
    ('identity:roles:update', '编辑角色', 'BUTTON', 'admin-console-identity-roles-edit', NULL, NULL, 80, 'ACTIVE'),
    ('identity:roles:grant', '分配角色权限', 'BUTTON', 'admin-console-identity-roles-save', NULL, NULL, 90, 'ACTIVE'),
    ('identity:roles:delete', '删除角色', 'BUTTON', 'admin-console-identity-roles-delete', NULL, NULL, 100, 'ACTIVE'),
    ('identity:history:read', '查看登录历史', 'API', '/api/v1/console/login-history', 'GET', NULL, 110, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
