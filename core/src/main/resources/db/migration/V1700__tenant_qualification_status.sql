-- Phase 08: qualification facts, bounded contact challenges, and immutable safe events.
ALTER TABLE tenants
    MODIFY verification_status ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED','SUPPLEMENT_REQUIRED')
        NOT NULL DEFAULT 'UNVERIFIED';

ALTER TABLE tenants ADD COLUMN trademark_use BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD COLUMN qualification_submitted_at DATETIME(6) NULL;
ALTER TABLE tenants ADD COLUMN qualification_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenants ADD COLUMN qualification_reason VARCHAR(500) NULL;
ALTER TABLE tenants ADD COLUMN initial_admin_user_id BIGINT UNSIGNED NULL;
ALTER TABLE tenants ADD CONSTRAINT uk_tenant_initial_admin UNIQUE (initial_admin_user_id);
ALTER TABLE tenants ADD CONSTRAINT fk_tenant_initial_admin FOREIGN KEY (initial_admin_user_id) REFERENCES users(id);

CREATE TABLE tenant_contact_verification_challenges (
    challenge_id CHAR(36) PRIMARY KEY,
    phone_encrypted VARBINARY(255) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    verified_at DATETIME NULL,
    consumed_at DATETIME NULL,
    request_ip VARCHAR(45) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_contact_challenge_attempts CHECK (attempt_count BETWEEN 0 AND 5),
    CONSTRAINT chk_contact_challenge_consumed CHECK (consumed_at IS NULL OR verified_at IS NOT NULL),
    KEY idx_contact_challenge_expiry (expires_at),
    KEY idx_contact_challenge_ip (request_ip, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tenant_qualification_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(64) NOT NULL,
    before_verification_status VARCHAR(32),
    after_verification_status VARCHAR(32),
    before_lifecycle_status VARCHAR(32),
    after_lifecycle_status VARCHAR(32),
    before_account_status VARCHAR(32),
    after_account_status VARCHAR(32),
    changed_fields VARCHAR(1000),
    reason VARCHAR(500),
    actor VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_qualification_event_tenant (tenant_id, created_at),
    CONSTRAINT fk_qualification_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL immutable event guards
CREATE TRIGGER tenant_qualification_events_no_update
BEFORE UPDATE ON tenant_qualification_events FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'qualification events are immutable';

CREATE TRIGGER tenant_qualification_events_no_delete
BEFORE DELETE ON tenant_qualification_events FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'qualification events are immutable';
