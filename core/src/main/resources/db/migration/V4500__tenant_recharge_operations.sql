CREATE TABLE tenant_recharge_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    amount_mil BIGINT NOT NULL,
    recharge_method VARCHAR(32) NOT NULL,
    transaction_ref_hash CHAR(64) NOT NULL,
    transaction_ref_mask VARCHAR(64) NOT NULL,
    evidence_text VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    submitter_actor VARCHAR(64) NOT NULL,
    reviewer_actor VARCHAR(64) NULL,
    review_reason VARCHAR(255) NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recharge_transaction_ref (transaction_ref_hash),
    KEY idx_recharge_tenant_state (tenant_id, status, created_at),
    KEY idx_recharge_review_state (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 36 tenant recharge request and finance review records';
