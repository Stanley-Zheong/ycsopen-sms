CREATE TABLE tenant_price_books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    price_book_version VARCHAR(64) NOT NULL UNIQUE,
    product_code VARCHAR(64) NOT NULL,
    unit_price_mil BIGINT NOT NULL,
    tier_rule_json JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 37 immutable tenant price book versions';

CREATE TABLE tenant_contracts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL UNIQUE,
    billing_mode VARCHAR(16) NOT NULL,
    price_book_version VARCHAR(64) NOT NULL,
    contract_no VARCHAR(64) NOT NULL UNIQUE,
    signed_at DATE NOT NULL,
    attachment_ref VARCHAR(255) NOT NULL,
    credit_limit_mil BIGINT NULL,
    billing_period VARCHAR(16) NULL,
    contract_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    approved_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant_contract_mode (billing_mode, contract_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 37 tenant contract pricing and billing mode';

CREATE TABLE postpaid_usage_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_doc_id VARCHAR(64) NOT NULL UNIQUE,
    billing_period VARCHAR(16) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    amount_mil BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'RECORDED',
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_postpaid_usage_period (tenant_id, period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 37 postpaid usage against contract credit limit';

INSERT INTO tenant_price_books(price_book_version, product_code, unit_price_mil, tier_rule_json, status)
SELECT 'SMS_STANDARD_V1', 'SMS', 50, NULL, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM tenant_price_books WHERE price_book_version='SMS_STANDARD_V1');
