CREATE TABLE channel_health_observations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    channel_id BIGINT UNSIGNED NOT NULL,
    connected TINYINT(1) NOT NULL,
    timeout_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
    failure_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
    average_latency_ms BIGINT NOT NULL DEFAULT 0,
    result_status ENUM('SUCCESS','FAILED') NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    observed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_channel_health_latest (channel_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase11 channel health observations';
