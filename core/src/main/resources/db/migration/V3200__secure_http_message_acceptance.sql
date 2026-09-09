ALTER TABLE message_submissions
    ADD COLUMN request_digest CHAR(64) NULL;

CREATE TABLE message_send_outbox (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    task_id BIGINT UNSIGNED NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    channel_id BIGINT UNSIGNED NOT NULL,
    state ENUM('READY','CLAIMED','SENT','FAILED') NOT NULL DEFAULT 'READY',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_send_outbox_task (task_id),
    UNIQUE KEY uk_message_send_outbox_message (message_id),
    KEY idx_message_send_outbox_state (state, created_at),
    KEY idx_message_send_outbox_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F-6.1 HTTP accepted send intent outbox';

CREATE INDEX idx_message_tasks_submit_id ON message_tasks(submit_id);
