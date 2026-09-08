CREATE TABLE channel_configuration_versions (
    id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    channel_id  BIGINT UNSIGNED NOT NULL,
    payload_json JSON NOT NULL,
    status      ENUM('CREATED','REJECTED','EFFECTIVE','STALE','FAILED') NOT NULL DEFAULT 'CREATED',
    reason_code VARCHAR(64) NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_channel_created (channel_id, created_at),
    CONSTRAINT fk_channel_configuration_versions_channel
        FOREIGN KEY (channel_id) REFERENCES channels(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase10 immutable channel configuration versions';
