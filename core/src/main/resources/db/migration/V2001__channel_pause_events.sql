CREATE TABLE channel_pause_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    channel_id BIGINT UNSIGNED NOT NULL,
    event_type ENUM('PAUSE','HEALTH_FAILURE','MAINTENANCE_START','MAINTENANCE_END') NOT NULL,
    trigger_type ENUM('MANUAL','HEALTH','COMPLAINT','RATIO') NOT NULL,
    actor VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    source_event_key VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_pause_source_event (source_event_key),
    KEY idx_channel_pause_channel (channel_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase11 channel pause and maintenance evidence';
