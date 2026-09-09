ALTER TABLE signatures
    MODIFY audit_status ENUM('PENDING','APPROVED','REJECTED','SUPPLEMENT_REQUIRED') NOT NULL DEFAULT 'PENDING';

ALTER TABLE signature_channel_registrations
    ADD COLUMN provider_request_id VARCHAR(96) NULL,
    ADD COLUMN result_message VARCHAR(255) NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN requested_by VARCHAR(64) NULL,
    ADD COLUMN last_attempt_at DATETIME(6) NULL;

CREATE TABLE signature_review_history (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    signature_id  BIGINT UNSIGNED NOT NULL,
    event_type    ENUM('SUBMITTED','APPROVED','REJECTED','SUPPLEMENT_REQUIRED') NOT NULL,
    actor         VARCHAR(64) NOT NULL,
    opinion       VARCHAR(500) NOT NULL,
    risk_level    ENUM('LOW','MEDIUM','HIGH') NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_signature_review_history_signature (signature_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 12 signature review history';
