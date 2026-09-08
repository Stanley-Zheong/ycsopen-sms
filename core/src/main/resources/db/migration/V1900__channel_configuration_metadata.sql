ALTER TABLE channels
    MODIFY channel_name VARCHAR(50) NOT NULL;

ALTER TABLE channels
    ADD UNIQUE KEY uk_channels_channel_name (channel_name);

ALTER TABLE channels
    MODIFY status ENUM('NORMAL','MAINTENANCE','ABNORMAL','PAUSED','OFFLINE') NOT NULL DEFAULT 'NORMAL';

ALTER TABLE channels
    ADD COLUMN tps_limit INT NOT NULL DEFAULT 100 AFTER window_size,
    ADD COLUMN availability VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' AFTER active_window,
    ADD COLUMN effective_version_id BIGINT UNSIGNED NULL AFTER extra_config,
    ADD COLUMN configuration_version BIGINT NOT NULL DEFAULT 0 AFTER effective_version_id,
    ADD COLUMN offline_by VARCHAR(64) NULL AFTER resumed_at,
    ADD COLUMN offline_at DATETIME NULL AFTER offline_by;
