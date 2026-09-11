ALTER TABLE statements ADD COLUMN statement_no VARCHAR(64) NULL;
ALTER TABLE statements ADD COLUMN billing_mode VARCHAR(16) NULL;
ALTER TABLE statements ADD COLUMN price_book_version VARCHAR(64) NULL;
ALTER TABLE statements ADD COLUMN source_snapshot_json TEXT NULL;
ALTER TABLE statements ADD COLUMN tenant_confirmed_at TIMESTAMP NULL;
ALTER TABLE statements ADD COLUMN finance_confirmed_at TIMESTAMP NULL;
ALTER TABLE statements ADD COLUMN confirmed_by_tenant VARCHAR(64) NULL;
ALTER TABLE statements ADD COLUMN confirmed_by_finance VARCHAR(64) NULL;
ALTER TABLE statements ADD COLUMN resolution_note VARCHAR(500) NULL;
ALTER TABLE statements ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE statements ADD CONSTRAINT uk_statement_tenant_period UNIQUE (tenant_id, period_start, period_end);

CREATE TABLE statement_differences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    statement_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    difference_type VARCHAR(32) NOT NULL,
    claimed_amount_mil BIGINT NOT NULL,
    note VARCHAR(500) NOT NULL,
    evidence_ref VARCHAR(255) NOT NULL,
    owner_actor VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    KEY idx_statement_difference_statement (statement_id, status),
    KEY idx_statement_difference_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 38 reconciliation difference evidence';

CREATE TABLE settlement_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    statement_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    amount_mil BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_SETTLEMENT',
    start_evidence VARCHAR(255) NOT NULL,
    complete_evidence VARCHAR(255) NULL,
    received_evidence VARCHAR(255) NULL,
    started_by VARCHAR(64) NOT NULL,
    completed_by VARCHAR(64) NULL,
    received_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    received_at TIMESTAMP NULL,
    UNIQUE KEY uk_settlement_statement (statement_id),
    KEY idx_settlement_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 38 postpaid settlement state';

ALTER TABLE invoices ADD COLUMN statement_id BIGINT NULL;
ALTER TABLE invoices ADD COLUMN request_evidence VARCHAR(255) NULL;
ALTER TABLE invoices ADD COLUMN requested_by VARCHAR(64) NULL;
ALTER TABLE invoices ADD COLUMN issued_by VARCHAR(64) NULL;
ALTER TABLE invoices ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE invoices ADD CONSTRAINT uk_invoice_no UNIQUE (invoice_no);
CREATE INDEX idx_invoice_statement ON invoices(statement_id);
CREATE INDEX idx_invoice_tenant_status ON invoices(tenant_id, status);
