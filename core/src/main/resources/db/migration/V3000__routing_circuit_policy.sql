CREATE TABLE routing_policy_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_no VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    source_name VARCHAR(64) NOT NULL,
    effective_at TIMESTAMP NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE routing_policy_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    priority INT NOT NULL,
    condition_type VARCHAR(32) NOT NULL,
    condition_value VARCHAR(128) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_ref VARCHAR(128) NOT NULL,
    weight INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_routing_policy_rules_version FOREIGN KEY (version_id) REFERENCES routing_policy_versions(id)
);

CREATE TABLE routing_circuit_states (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_code VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    failure_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL DEFAULT 0,
    opened_at TIMESTAMP NULL,
    history VARCHAR(512) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE routing_retry_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    normalized_category VARCHAR(64) NOT NULL UNIQUE,
    retryable BOOLEAN NOT NULL,
    delay_seconds INT NOT NULL,
    max_attempts INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE routing_decision_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_no VARCHAR(32) NULL,
    tenant_id BIGINT NULL,
    carrier VARCHAR(32) NULL,
    prefix VARCHAR(16) NULL,
    content_keyword VARCHAR(64) NULL,
    target_type VARCHAR(32) NOT NULL,
    target_ref VARCHAR(128) NOT NULL,
    matched_rule_id BIGINT NULL,
    explanation VARCHAR(512) NOT NULL,
    circuit_status VARCHAR(32) NOT NULL,
    retry_policy VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO permissions(code, name, resource_type, description)
SELECT 'routing-policy:menu', '路由策略菜单', 'MENU', '访问路由策略页面'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='routing-policy:menu');

INSERT INTO permissions(code, name, resource_type, description)
SELECT 'routing-policy:read', '路由策略读取', 'API', '查看路由策略、模拟和熔断状态'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='routing-policy:read');

INSERT INTO permissions(code, name, resource_type, description)
SELECT 'routing-policy:write', '路由策略维护', 'API', '维护路由规则、熔断和重试策略'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='routing-policy:write');

INSERT INTO permissions(code, name, resource_type, description)
SELECT 'routing-policy:import', '路由策略导入', 'BUTTON', '导入路由规则版本'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='routing-policy:import');

