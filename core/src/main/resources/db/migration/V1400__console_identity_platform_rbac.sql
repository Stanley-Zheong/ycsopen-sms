-- Phase 5 identity persistence; namespace SCHEMA-P05 / V1400-V1499.
ALTER TABLE user_sessions
    ADD COLUMN revoked_at DATETIME NULL AFTER expires_at;

ALTER TABLE users
    ADD COLUMN valid_until DATE NULL AFTER password_expire_time;

CREATE TABLE login_history (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    username VARCHAR(50) NOT NULL,
    login_ip VARCHAR(45) NOT NULL,
    user_agent VARCHAR(512) NULL,
    outcome VARCHAR(32) NOT NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_login_history_user_time (user_id, occurred_at),
    KEY idx_login_history_username_time (username, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_change_history (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(32) NOT NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_account_change_user_time (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE identity_notification_outbox (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(32) NOT NULL,
    target_user_id BIGINT UNSIGNED NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_identity_notification_source (event_type, source_ref),
    KEY idx_identity_notification_status (status, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
