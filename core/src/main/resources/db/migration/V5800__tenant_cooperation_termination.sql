CREATE TABLE tenant_termination_requests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    reason ENUM('VOLUNTARY','SEVERE_VIOLATION','LONG_TERM_ARREARS','EXPIRED_CREDENTIALS') NOT NULL,
    request_evidence VARCHAR(1000) NOT NULL,
    request_status ENUM('BLOCKED_CLEARANCE','PENDING_ADMIN_APPROVAL','APPROVED','EFFECTIVE','REJECTED') NOT NULL,
    requested_by VARCHAR(64) NOT NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by VARCHAR(64) NULL,
    approved_at DATETIME NULL,
    admin_opinion VARCHAR(500) NULL,
    effective_at DATETIME NULL,
    clearance_snapshot_json JSON NOT NULL,
    participant_snapshot_json JSON NOT NULL,
    compensation_json JSON NULL,
    UNIQUE KEY uk_tenant_termination_active (tenant_id, request_status),
    KEY idx_tenant_termination_tenant_time (tenant_id, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 49 cooperation termination request, clearance, approval and effect state';

CREATE TABLE tenant_termination_participants (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    participant_code VARCHAR(64) NOT NULL,
    participant_name VARCHAR(100) NOT NULL,
    participant_state VARCHAR(32) NOT NULL,
    blocker_count BIGINT NOT NULL DEFAULT 0,
    evidence_json JSON NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_termination_participant (request_id, participant_code),
    KEY idx_termination_participant_tenant (tenant_id, participant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 49 machine-readable participant inventory and clearance evidence';

CREATE TABLE tenant_termination_audits (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    evidence_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_termination_audit_request (request_id, created_at),
    KEY idx_termination_audit_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 49 immutable termination audit and compensation evidence';

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'tenant-termination:menu', '合作终止菜单', 'MENU', '/admin/tenant/terminations', NULL, NULL, 5800, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='tenant-termination:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'tenant-termination:read', '查看合作终止', 'API', '/api/v1/console/tenant-terminations/**', 'GET', NULL, 5801, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='tenant-termination:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'tenant-termination:write', '申请合作终止', 'API', '/api/v1/console/tenant-terminations/**', 'POST', NULL, 5802, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='tenant-termination:write');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'tenant-termination:approve', '审批合作终止', 'BUTTON', '/admin/tenant/terminations', NULL, NULL, 5803, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='tenant-termination:approve');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'tenant-termination:effect', '生效合作终止', 'BUTTON', '/admin/tenant/terminations', NULL, NULL, 5804, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='tenant-termination:effect');
