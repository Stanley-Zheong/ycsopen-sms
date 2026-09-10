CREATE TABLE IF NOT EXISTS tenants (
    id BIGINT PRIMARY KEY,
    lifecycle_status VARCHAR(32),
    full_name VARCHAR(255),
    short_name VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS channels (
    id BIGINT PRIMARY KEY,
    status VARCHAR(32),
    channel_name VARCHAR(255),
    pause_reason VARCHAR(255),
    paused_by VARCHAR(64),
    paused_at DATETIME
);

CREATE TABLE IF NOT EXISTS signatures (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    audit_status VARCHAR(32),
    sign_content VARCHAR(255),
    audit_comment VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS templates (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    audit_status VARCHAR(32),
    template_name VARCHAR(255),
    audit_comment VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS complaints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source VARCHAR(32) NOT NULL,
    tenant_id BIGINT NULL,
    signature_id BIGINT NULL,
    template_id BIGINT NULL,
    message_id VARCHAR(64) NULL,
    channel_id BIGINT NULL,
    summary VARCHAR(500),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    handling_note VARCHAR(500),
    corrective_action VARCHAR(500),
    handled_by VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS disposal_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    disposal_type VARCHAR(64) NOT NULL,
    target_ref VARCHAR(64) NOT NULL,
    disposed_by VARCHAR(64),
    disposed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resumed_at DATETIME NULL,
    resume_condition VARCHAR(255)
);

ALTER TABLE complaints MODIFY COLUMN source VARCHAR(32) NOT NULL;
ALTER TABLE complaints ADD COLUMN content_type VARCHAR(64) NULL;
ALTER TABLE complaints ADD COLUMN complained_mobile VARCHAR(32) NULL;
ALTER TABLE complaints ADD COLUMN attribution_quality VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE complaints ADD COLUMN requirement VARCHAR(500) NULL;
ALTER TABLE complaints ADD COLUMN opinion VARCHAR(500) NULL;
ALTER TABLE complaints ADD COLUMN remediation VARCHAR(500) NULL;
ALTER TABLE complaints ADD COLUMN created_by VARCHAR(64) NULL;
ALTER TABLE complaints ADD COLUMN accepted_by VARCHAR(64) NULL;
ALTER TABLE complaints ADD COLUMN accepted_at DATETIME NULL;
ALTER TABLE complaints ADD COLUMN handled_at DATETIME NULL;
ALTER TABLE complaints ADD COLUMN closed_by VARCHAR(64) NULL;
ALTER TABLE complaints ADD COLUMN closed_at DATETIME NULL;
ALTER TABLE complaints ADD COLUMN closed_note VARCHAR(500) NULL;

ALTER TABLE disposal_records ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'APPLIED';
ALTER TABLE disposal_records ADD COLUMN authorized_review_id VARCHAR(128) NULL;
ALTER TABLE disposal_records ADD COLUMN failure_reason VARCHAR(500) NULL;
ALTER TABLE disposal_records ADD COLUMN recovered_by VARCHAR(64) NULL;
ALTER TABLE disposal_records ADD COLUMN recovered_at DATETIME NULL;
ALTER TABLE disposal_records ADD COLUMN original_complaint_id BIGINT NULL;
ALTER TABLE disposal_records ADD COLUMN idempotency_key VARCHAR(255) NULL;

CREATE INDEX idx_complaints_status ON complaints(status);
CREATE INDEX idx_complaints_quality ON complaints(attribution_quality);
CREATE INDEX idx_complaints_signature ON complaints(signature_id);
CREATE INDEX idx_complaints_template ON complaints(template_id);
CREATE INDEX idx_disposal_case_target ON disposal_records(complaint_id, disposal_type, target_ref);
CREATE UNIQUE INDEX uk_disposal_idempotency ON disposal_records(idempotency_key);
