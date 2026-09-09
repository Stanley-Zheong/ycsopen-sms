ALTER TABLE tenant_callback_configs ADD COLUMN config_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE tenant_callback_configs ADD COLUMN latest_failure_reason VARCHAR(255) NULL;
ALTER TABLE tenant_callback_configs ADD COLUMN paused_at DATETIME NULL;
ALTER TABLE tenant_callback_configs ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tenant_callback_configs ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE tenant_callback_configs ADD COLUMN callback_signing_secret VARCHAR(128) NULL;

CREATE TABLE webhook_delivery_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    logical_id VARCHAR(160) NOT NULL,
    destination_url VARCHAR(512) NOT NULL,
    envelope_version VARCHAR(16) NOT NULL DEFAULT 'v1',
    payload_json TEXT NOT NULL,
    signature VARCHAR(128) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paused_at DATETIME NULL,
    terminal_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_webhook_event_logical (tenant_id, event_type, logical_id),
    KEY idx_webhook_event_state_next (state, next_attempt_at),
    KEY idx_webhook_event_tenant_state (tenant_id, state, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase28 signed idempotent webhook delivery events';

CREATE TABLE webhook_delivery_attempts (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    attempt_no INT NOT NULL,
    destination_url VARCHAR(512) NOT NULL,
    http_status INT NULL,
    result_code VARCHAR(64) NOT NULL,
    result_message VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_webhook_attempt (event_id, attempt_no),
    KEY idx_webhook_attempt_event (event_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase28 webhook delivery attempt evidence';
