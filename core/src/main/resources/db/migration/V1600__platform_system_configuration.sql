-- Phase 07 typed platform configuration; namespace SCHEMA-P07 / V1600-V1699.
CREATE TABLE platform_configuration_versions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    base_version_id BIGINT UNSIGNED NULL,
    source_version_id BIGINT UNSIGNED NULL,
    status VARCHAR(24) NOT NULL,
    values_json JSON NOT NULL,
    changed_keys_json JSON NOT NULL,
    checksum CHAR(64) NOT NULL,
    reason VARCHAR(256) NOT NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_by BIGINT UNSIGNED NULL,
    activated_at DATETIME NULL,
    activation_reason VARCHAR(256) NULL,
    reload_status VARCHAR(24) NOT NULL DEFAULT 'NOT_APPLIED',
    reload_error_code VARCHAR(64) NULL,
    KEY idx_platform_config_status_version (status, id),
    KEY idx_platform_config_actor_time (created_by, created_at),
    CONSTRAINT chk_platform_config_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'ABANDONED', 'RELOAD_REJECTED')
    ),
    CONSTRAINT chk_platform_config_reload_status CHECK (
        reload_status IN ('NOT_APPLIED', 'PENDING', 'APPLIED', 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE platform_configuration_state (
    id TINYINT UNSIGNED PRIMARY KEY,
    active_version_id BIGINT UNSIGNED NULL,
    lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reload_status VARCHAR(24) NOT NULL DEFAULT 'APPLIED',
    reload_error_code VARCHAR(64) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_platform_config_singleton CHECK (id = 1),
    CONSTRAINT chk_platform_config_state_reload CHECK (
        reload_status IN ('PENDING', 'APPLIED', 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TRIGGER trg_platform_config_version_guard
BEFORE UPDATE ON platform_configuration_versions
FOR EACH ROW
BEGIN
    IF NOT (
        OLD.id <=> NEW.id
        AND OLD.base_version_id <=> NEW.base_version_id
        AND OLD.source_version_id <=> NEW.source_version_id
        AND OLD.values_json <=> NEW.values_json
        AND OLD.changed_keys_json <=> NEW.changed_keys_json
        AND OLD.checksum <=> NEW.checksum
        AND OLD.reason <=> NEW.reason
        AND OLD.created_by <=> NEW.created_by
        AND OLD.created_at <=> NEW.created_at
        AND (
            (
                OLD.status = 'DRAFT' AND NEW.status = 'ABANDONED'
                AND OLD.activated_by <=> NEW.activated_by
                AND OLD.activated_at <=> NEW.activated_at
                AND OLD.activation_reason <=> NEW.activation_reason
                AND OLD.reload_status <=> NEW.reload_status
                AND OLD.reload_error_code <=> NEW.reload_error_code
            )
            OR (
                OLD.status = 'DRAFT' AND NEW.status = 'ACTIVE'
                AND NEW.activated_by IS NOT NULL
                AND NEW.activated_at IS NOT NULL
                AND NEW.activation_reason IS NOT NULL
                AND NEW.reload_status = 'PENDING'
                AND NEW.reload_error_code IS NULL
            )
            OR (
                OLD.status = 'DRAFT' AND NEW.status = 'RELOAD_REJECTED'
                AND OLD.activated_by <=> NEW.activated_by
                AND OLD.activated_at <=> NEW.activated_at
                AND OLD.activation_reason <=> NEW.activation_reason
                AND NEW.reload_status = 'REJECTED'
                AND NEW.reload_error_code IS NOT NULL
            )
            OR (
                OLD.status = 'ACTIVE' AND NEW.status = 'SUPERSEDED'
                AND OLD.activated_by <=> NEW.activated_by
                AND OLD.activated_at <=> NEW.activated_at
                AND OLD.activation_reason <=> NEW.activation_reason
                AND OLD.reload_status <=> NEW.reload_status
                AND OLD.reload_error_code <=> NEW.reload_error_code
            )
            OR (
                OLD.status = 'ACTIVE' AND NEW.status = 'ACTIVE'
                AND OLD.activated_by <=> NEW.activated_by
                AND OLD.activated_at <=> NEW.activated_at
                AND OLD.activation_reason <=> NEW.activation_reason
                AND OLD.reload_status = 'PENDING'
                AND NEW.reload_status = 'APPLIED'
                AND NEW.reload_error_code IS NULL
            )
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'platform configuration versions are immutable outside lifecycle transitions';
    END IF;
END;

CREATE TRIGGER trg_platform_config_version_no_delete
BEFORE DELETE ON platform_configuration_versions
FOR EACH ROW
SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'platform configuration versions cannot be deleted';

INSERT INTO platform_configuration_state
    (id, active_version_id, lock_version, reload_status)
VALUES (1, NULL, 0, 'APPLIED');
