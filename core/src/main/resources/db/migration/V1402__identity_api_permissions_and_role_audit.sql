-- Phase 5 separates UI-button visibility from server API authorization and records role changes.
INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('identity:accounts:create:api', '创建平台账号接口', 'API', '/api/v1/console/platform-accounts', 'POST', NULL, 120, 'ACTIVE'),
    ('identity:accounts:update:api', '更新平台账号接口', 'API', '/api/v1/console/platform-accounts/{userId}', 'PUT', NULL, 130, 'ACTIVE'),
    ('identity:accounts:state:api', '变更平台账号状态接口', 'API', '/api/v1/console/platform-accounts/{userId}/{action}', 'POST', NULL, 140, 'ACTIVE'),
    ('identity:roles:create:api', '创建平台角色接口', 'API', '/api/v1/console/platform-roles', 'POST', NULL, 150, 'ACTIVE'),
    ('identity:roles:update:api', '更新平台角色接口', 'API', '/api/v1/console/platform-roles/{roleId}', 'PUT', NULL, 160, 'ACTIVE'),
    ('identity:roles:grant:api', '分配平台角色权限接口', 'API', '/api/v1/console/platform-roles/{roleId}/permissions', 'PUT', NULL, 170, 'ACTIVE'),
    ('identity:roles:delete:api', '删除平台角色接口', 'API', '/api/v1/console/platform-roles/{roleId}', 'DELETE', NULL, 180, 'ACTIVE'),
    ('identity:history:all', '全量登录历史数据范围', 'DATA', 'login-history:*', NULL, NULL, 190, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);

CREATE TABLE role_change_history (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_state TEXT NULL,
    after_state TEXT NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_role_change_role_time (role_id, occurred_at),
    KEY idx_role_change_actor_time (actor_user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
