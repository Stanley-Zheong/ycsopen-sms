CREATE TABLE fee_warning_rules (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    rule_name VARCHAR(128) NOT NULL,
    tenant_id BIGINT NULL,
    metric_type VARCHAR(32) NOT NULL,
    threshold_value DECIMAL(12,4) NOT NULL,
    action VARCHAR(32) NOT NULL,
    notify_channels VARCHAR(500) NOT NULL,
    notification_targets VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_fee_warning_rules_scope (tenant_id, metric_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 40 fee warning rules';

CREATE TABLE fee_warning_episodes (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    alert_record_id BIGINT NULL,
    metric_type VARCHAR(32) NOT NULL,
    source_key VARCHAR(128) NOT NULL,
    source_amount_mil BIGINT NOT NULL,
    credit_limit_mil BIGINT NULL,
    used_amount_mil BIGINT NULL,
    threshold_value DECIMAL(12,4) NOT NULL,
    ratio DECIMAL(12,4) NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    approval_state VARCHAR(32) NOT NULL,
    delivery_state VARCHAR(32) NOT NULL,
    source_snapshot VARCHAR(1000) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    resolution_note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fee_warning_source_key (source_key),
    KEY idx_fee_warning_tenant_state (tenant_id, status, approval_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 40 deduplicated fee warning episodes';

INSERT INTO alert_rules(rule_name,rule_type,metric_name,metric_source,threshold_value,comparison_op,
    duration_minutes,severity,notify_channels,notification_targets,source_scope,status,created_by)
SELECT '费用预警传输','FEE_WARNING','BALANCE','fee_warning_episodes',0,'>=',
    1,'HIGH','["EMAIL"]','["finance"]','PLATFORM','ACTIVE','system'
WHERE NOT EXISTS (SELECT 1 FROM alert_rules WHERE rule_type='FEE_WARNING');
