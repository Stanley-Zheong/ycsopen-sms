ALTER TABLE unsubscribe_keywords
    ADD COLUMN keyword_normalized VARCHAR(64) NULL,
    ADD COLUMN scope_key VARCHAR(80) NULL,
    ADD COLUMN created_by VARCHAR(64) NULL,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

UPDATE unsubscribe_keywords
   SET keyword_normalized = UPPER(TRIM(keyword)),
       scope_key = CASE WHEN scope='GLOBAL' THEN 'GLOBAL' ELSE CONCAT('TENANT:', tenant_id) END
 WHERE keyword_normalized IS NULL OR scope_key IS NULL;

INSERT INTO unsubscribe_keywords(keyword, keyword_normalized, scope, scope_key, status, created_by)
SELECT 'TD', 'TD', 'GLOBAL', 'GLOBAL', 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM unsubscribe_keywords WHERE scope_key='GLOBAL' AND keyword_normalized='TD');

INSERT INTO unsubscribe_keywords(keyword, keyword_normalized, scope, scope_key, status, created_by)
SELECT '退订', '退订', 'GLOBAL', 'GLOBAL', 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM unsubscribe_keywords WHERE scope_key='GLOBAL' AND keyword_normalized='退订');

INSERT INTO unsubscribe_keywords(keyword, keyword_normalized, scope, scope_key, status, created_by)
SELECT 'QUIT', 'QUIT', 'GLOBAL', 'GLOBAL', 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM unsubscribe_keywords WHERE scope_key='GLOBAL' AND keyword_normalized='QUIT');

INSERT INTO unsubscribe_keywords(keyword, keyword_normalized, scope, scope_key, status, created_by)
SELECT 'UNSUBSCRIBE', 'UNSUBSCRIBE', 'GLOBAL', 'GLOBAL', 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM unsubscribe_keywords WHERE scope_key='GLOBAL' AND keyword_normalized='UNSUBSCRIBE');

ALTER TABLE unsubscribe_keywords
    MODIFY keyword_normalized VARCHAR(64) NOT NULL,
    MODIFY scope_key VARCHAR(80) NOT NULL,
    ADD UNIQUE KEY uk_unsubscribe_keyword_scope (scope_key, keyword_normalized),
    ADD KEY idx_unsubscribe_keyword_scope_status (scope, tenant_id, status);

ALTER TABLE unsubscribe_records
    ADD COLUMN masked_mobile VARCHAR(32) NULL,
    ADD COLUMN method VARCHAR(24) NOT NULL DEFAULT 'UPLINK',
    ADD COLUMN product_code VARCHAR(64) NULL,
    ADD COLUMN handling_state VARCHAR(32) NOT NULL DEFAULT 'TENANT_BLACKLISTED',
    ADD COLUMN notification_state VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED',
    ADD COLUMN notification_event_id BIGINT UNSIGNED NULL,
    ADD COLUMN confirmation_state VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN reply_event_id BIGINT UNSIGNED NULL,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD UNIQUE KEY uk_unsubscribe_uplink_record (uplink_record_id),
    ADD KEY idx_unsubscribe_signature_time (tenant_id, signature_id, unsubscribed_at),
    ADD KEY idx_unsubscribe_product_time (tenant_id, product_code, unsubscribed_at),
    ADD KEY idx_unsubscribe_notification (notification_state, unsubscribed_at);

UPDATE unsubscribe_records
   SET masked_mobile = COALESCE(masked_mobile, '***')
 WHERE masked_mobile IS NULL;

CREATE TABLE unsubscribe_alert_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    signature_id BIGINT UNSIGNED NULL,
    product_code VARCHAR(64) NULL,
    period_start DATETIME NOT NULL,
    period_end DATETIME NOT NULL,
    unsubscribe_count INT NOT NULL,
    final_sent_count INT NOT NULL,
    rate DECIMAL(12,6) NOT NULL,
    threshold_rate DECIMAL(12,6) NOT NULL,
    formula VARCHAR(128) NOT NULL,
    freshness_at DATETIME NOT NULL,
    source_event VARCHAR(64) NOT NULL DEFAULT 'UNSUBSCRIBE_RATE_ABNORMAL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_unsubscribe_alert_tenant_time (tenant_id, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 33 unsubscribe abnormal rate alert evidence';
