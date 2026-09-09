-- Phase 08 safe inspection facts and exact tenant qualification permissions.
ALTER TABLE tenants ADD COLUMN inspection_status ENUM('NOT_STARTED','COMPLETED','FAILED') NOT NULL DEFAULT 'NOT_STARTED';
ALTER TABLE tenants ADD COLUMN inspection_company_name VARCHAR(100) NULL;
ALTER TABLE tenants ADD COLUMN inspection_credit_code CHAR(18) NULL;
ALTER TABLE tenants ADD COLUMN inspection_confidence DECIMAL(5,4) NULL;
ALTER TABLE tenants ADD COLUMN inspection_provider_request_id VARCHAR(100) NULL;
ALTER TABLE tenants ADD COLUMN inspection_completed_at DATETIME(6) NULL;

INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('tenant:menu', '机构管理菜单', 'MENU', '/admin/tenants', NULL, NULL, 310, 'ACTIVE'),
    ('tenant:read', '查看机构安全资料', 'API', '/api/v1/console/admin/tenants', 'GET', NULL, 320, 'ACTIVE'),
    ('tenant:qualification:review', '审核机构资质', 'BUTTON', 'admin-tenant-qualification-tenants-review-open', 'POST', NULL, 330, 'ACTIVE'),
    ('tenant:update', '维护机构资料', 'BUTTON', 'admin-tenant-qualification-tenants-edit-open', 'PATCH', NULL, 340, 'ACTIVE'),
    ('tenant:status:update', '变更机构运行状态', 'BUTTON', 'admin-tenant-qualification-tenants-status-action', 'POST', NULL, 350, 'ACTIVE'),
    ('tenant:evidence:read', '查看机构受保护证据', 'API', '/api/v1/console/admin/tenants/{tenantId}/evidence/{kind}', 'GET', NULL, 360, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
