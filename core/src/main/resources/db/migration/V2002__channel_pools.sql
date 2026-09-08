CREATE TABLE channel_pools (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    pool_name VARCHAR(64) NOT NULL,
    mode ENUM('WEIGHTED','PRIMARY_BACKUP') NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_pools_name (pool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase11 channel pools';

CREATE TABLE channel_pool_members (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    pool_id BIGINT UNSIGNED NOT NULL,
    channel_id BIGINT UNSIGNED NOT NULL,
    weight INT NOT NULL DEFAULT 0,
    primary_member TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_pool_member (pool_id, channel_id),
    KEY idx_channel_pool_member_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase11 channel pool members';
