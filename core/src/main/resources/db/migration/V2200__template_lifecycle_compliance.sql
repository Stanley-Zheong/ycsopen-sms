ALTER TABLE templates
    MODIFY audit_status ENUM('PENDING','APPROVED','REJECTED','AMENDMENT_REQUIRED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN variable_names VARCHAR(255) NULL,
    ADD COLUMN version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN previous_template_id BIGINT UNSIGNED NULL,
    ADD UNIQUE KEY uk_templates_previous_template (previous_template_id);

CREATE TABLE template_review_history (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    opinion VARCHAR(500) NOT NULL,
    snapshot_content VARCHAR(500) NOT NULL,
    variable_names VARCHAR(255),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_template_review_history_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 13 template lifecycle review history';
