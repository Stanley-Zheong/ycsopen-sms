CREATE TABLE trial_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    quota_total INT NOT NULL,
    quota_remaining INT NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    version INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trial_consumption_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    message_ref VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    quota_delta INT NOT NULL,
    amount_mil BIGINT NOT NULL DEFAULT 0,
    entry_type VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_trial_consumption_message (tenant_id, message_ref)
);

CREATE TABLE prepaid_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL UNIQUE,
    balance_mil BIGINT NOT NULL,
    frozen_mil BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE prepaid_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_doc_id VARCHAR(64) NOT NULL UNIQUE,
    business_type VARCHAR(64) NOT NULL,
    channel_code VARCHAR(64) NULL,
    price_mil BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount_mil BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    transaction_ref VARCHAR(128) NULL,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE balance_audit_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_doc_id VARCHAR(64) NOT NULL,
    mutation_type VARCHAR(32) NOT NULL,
    amount_mil BIGINT NOT NULL,
    before_balance_mil BIGINT NOT NULL,
    after_balance_mil BIGINT NOT NULL,
    before_frozen_mil BIGINT NOT NULL,
    after_frozen_mil BIGINT NOT NULL,
    account_version INT NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trial_conversion_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    trial_status VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, status)
SELECT 'trial-prepaid:menu', '试用预付费菜单', 'MENU', '/admin/tenant-trial-contracts', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='trial-prepaid:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, status)
SELECT 'trial-prepaid:read', '试用预付费读取', 'API', '/api/trial-prepaid', 'GET', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='trial-prepaid:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, status)
SELECT 'trial-prepaid:write', '试用预付费维护', 'API', '/api/trial-prepaid', 'POST', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='trial-prepaid:write');
