CREATE TABLE statistics_metric_registry (
    metric_code VARCHAR(64) PRIMARY KEY,
    metric_name VARCHAR(128) NOT NULL,
    source_tables VARCHAR(255) NOT NULL,
    formula VARCHAR(255) NOT NULL,
    freshness_rule VARCHAR(128) NOT NULL,
    permission_scope VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
    formula_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 34 metric source/formula/freshness registry';

INSERT INTO statistics_metric_registry(metric_code, metric_name, source_tables, formula, freshness_rule, permission_scope, formula_version)
SELECT 'RESOURCE_USAGE', '签名模板使用与拒绝指标', 'message_submits,message_tasks,signatures,templates',
       'accepted/rejected/final message states grouped by signature/template', 'freshness_at >= latest source updated_at', 'PLATFORM', 'v1'
WHERE NOT EXISTS (SELECT 1 FROM statistics_metric_registry WHERE metric_code='RESOURCE_USAGE');

INSERT INTO statistics_metric_registry(metric_code, metric_name, source_tables, formula, freshness_rule, permission_scope, formula_version)
SELECT 'CHANNEL_DELIVERY', '通道发送成功成本延迟指标', 'message_tasks,delivery_reports,billing_records,message_send_outbox',
       'send/success/failure/cost/latency grouped by channel and geography', 'freshness_at >= latest source updated_at', 'PLATFORM', 'v1'
WHERE NOT EXISTS (SELECT 1 FROM statistics_metric_registry WHERE metric_code='CHANNEL_DELIVERY');

INSERT INTO statistics_metric_registry(metric_code, metric_name, source_tables, formula, freshness_rule, permission_scope, formula_version)
SELECT 'TENANT_BEHAVIOR', '租户发送消费活跃指标', 'message_submits,message_tasks,billing_records',
       'accepted/rejected/send/success/failure/consumption grouped by tenant', 'freshness_at >= latest source updated_at', 'TENANT', 'v1'
WHERE NOT EXISTS (SELECT 1 FROM statistics_metric_registry WHERE metric_code='TENANT_BEHAVIOR');

CREATE TABLE statistics_aggregates (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    metric_code VARCHAR(64) NOT NULL,
    bucket_grain ENUM('HOUR','DATE') NOT NULL DEFAULT 'HOUR',
    bucket_start DATETIME NOT NULL,
    bucket_date DATE NOT NULL,
    tenant_id BIGINT UNSIGNED NULL,
    channel_id BIGINT UNSIGNED NULL,
    carrier VARCHAR(32) NULL,
    message_type VARCHAR(32) NULL,
    province VARCHAR(64) NULL,
    city VARCHAR(64) NULL,
    signature_id BIGINT UNSIGNED NULL,
    template_id BIGINT UNSIGNED NULL,
    submit_count INT NOT NULL DEFAULT 0,
    accepted_count INT NOT NULL DEFAULT 0,
    rejected_count INT NOT NULL DEFAULT 0,
    send_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    fee_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
    avg_response_ms BIGINT NOT NULL DEFAULT 0,
    source_version BIGINT NOT NULL DEFAULT 0,
    correction_identity VARCHAR(128) NOT NULL,
    drilldown_key VARCHAR(255) NOT NULL,
    formula_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    freshness_at DATETIME NOT NULL,
    quality_state ENUM('FRESH','STALE','CORRECTED') NOT NULL DEFAULT 'FRESH',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_statistics_aggregate_metric_bucket (metric_code, bucket_start),
    KEY idx_statistics_aggregate_tenant_bucket (tenant_id, bucket_start),
    KEY idx_statistics_aggregate_channel_bucket (channel_id, bucket_start),
    KEY idx_statistics_aggregate_resource_bucket (signature_id, template_id, bucket_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 34 source-backed statistics aggregates';

CREATE TABLE statistics_correction_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    source_table VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    previous_state VARCHAR(64) NULL,
    current_state VARCHAR(64) NOT NULL,
    correction_identity VARCHAR(128) NOT NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_statistics_correction_identity (correction_identity),
    KEY idx_statistics_correction_source (source_table, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 34 late/corrected aggregate source events';
