ALTER TABLE alert_rules
    ADD COLUMN metric_source VARCHAR(64) NOT NULL DEFAULT 'statistics_aggregates',
    ADD COLUMN notification_targets JSON NULL,
    ADD COLUMN source_scope VARCHAR(64) NOT NULL DEFAULT 'PLATFORM',
    ADD COLUMN created_by VARCHAR(64) NULL,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE alert_records
    ADD COLUMN severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN source_module VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN source_key VARCHAR(128) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN impact_scope VARCHAR(255) NULL,
    ADD COLUMN acknowledged_by VARCHAR(64) NULL,
    ADD COLUMN resolved_by VARCHAR(64) NULL,
    ADD COLUMN resolution_note VARCHAR(255) NULL,
    ADD COLUMN delivery_state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN muted_until DATETIME NULL,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD KEY idx_alert_records_source_state (rule_id, source_key, status),
    ADD KEY idx_alert_records_severity_time (severity, triggered_at);

CREATE TABLE alert_delivery_attempts (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    alert_record_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(32) NOT NULL,
    target_snapshot VARCHAR(500) NOT NULL,
    provider_result VARCHAR(64) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    status ENUM('DELIVERED','FAILED','MUTED') NOT NULL DEFAULT 'DELIVERED',
    failure_reason VARCHAR(255) NULL,
    attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_alert_delivery_record (alert_record_id),
    KEY idx_alert_delivery_status (status, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 35 alert notification delivery attempts';

CREATE TABLE alert_mutes (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    scope VARCHAR(64) NOT NULL DEFAULT 'GLOBAL',
    reason VARCHAR(255) NOT NULL,
    muted_by VARCHAR(64) NOT NULL,
    muted_until DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_alert_mute_active (scope, muted_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 35 bounded global alert notification mute';
