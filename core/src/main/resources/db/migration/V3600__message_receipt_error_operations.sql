CREATE TABLE message_operation_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    operation_key VARCHAR(180) NOT NULL,
    action_id VARCHAR(64) NOT NULL,
    action_type ENUM('RESEND','APPEAL','RECEIPT_CORRECT','RECEIPT_REPLAY','BULK_RETRY','MARK_PROBLEM','EXPORT_REQUEST') NOT NULL,
    target_message_id VARCHAR(64) NULL,
    target_task_id BIGINT UNSIGNED NULL,
    target_receipt_id BIGINT UNSIGNED NULL,
    actor VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status ENUM('REQUESTED','COMPLETED','FAILED','DUPLICATE') NOT NULL DEFAULT 'REQUESTED',
    result_code VARCHAR(64) NULL,
    result_message VARCHAR(500) NULL,
    snapshot_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_message_operation_key (operation_key),
    KEY idx_message_operation_message (target_message_id, created_at),
    KEY idx_message_operation_receipt (target_receipt_id, created_at),
    KEY idx_message_operation_type (action_type, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 27 idempotent message, receipt, and error operation evidence';

INSERT INTO permissions
    (permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
VALUES
    ('message-ops:menu', '消息回执错误运营菜单', 'MENU', '/admin/records', NULL, NULL, 500, 'ACTIVE'),
    ('message-ops:read', '查看提交发送回执错误详情', 'API', '/api/v1/console/message-operations', 'GET', NULL, 501, 'ACTIVE'),
    ('message-ops:action', '执行重发申诉纠正重放标记', 'API', '/api/v1/console/message-operations', 'POST', NULL, 502, 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        resource_path = VALUES(resource_path),
                        http_method = VALUES(http_method),
                        sort_order = VALUES(sort_order),
                        status = VALUES(status);
