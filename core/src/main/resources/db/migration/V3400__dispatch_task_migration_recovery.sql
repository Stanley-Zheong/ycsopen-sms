CREATE TABLE dispatch_recovery_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    new_task_id BIGINT UNSIGNED NULL,
    from_channel_id BIGINT UNSIGNED NULL,
    to_channel_id BIGINT UNSIGNED NULL,
    action ENUM('MIGRATED','RETRY_CREATED','NO_BACKUP') NOT NULL,
    actor VARCHAR(64) NOT NULL,
    evidence VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dispatch_recovery_once (task_id, action),
    KEY idx_dispatch_recovery_task (task_id, created_at),
    KEY idx_dispatch_recovery_channel (from_channel_id, to_channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase25 dispatch task migration and retry evidence';

CREATE TABLE channel_recovery_tests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    channel_id BIGINT UNSIGNED NOT NULL,
    success TINYINT(1) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    evidence VARCHAR(500) NOT NULL,
    tested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_channel_recovery_tests_channel (channel_id, tested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase25 small-traffic channel recovery evidence';
