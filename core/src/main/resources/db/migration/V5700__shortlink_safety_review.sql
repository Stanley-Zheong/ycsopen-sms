ALTER TABLE short_links ADD target_version INT NOT NULL DEFAULT 1;
ALTER TABLE short_links ADD short_url VARCHAR(255) NULL;
ALTER TABLE short_links ADD immutable_target_sha256 CHAR(64) NULL;
ALTER TABLE short_links ADD automated_result_json JSON NULL;
ALTER TABLE short_links ADD screenshot_evidence_ref VARCHAR(255) NULL;
ALTER TABLE short_links ADD domain_evidence_json JSON NULL;
ALTER TABLE short_links ADD risk_level ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW';
ALTER TABLE short_links ADD review_opinion VARCHAR(255) NULL;
ALTER TABLE short_links ADD reviewed_by VARCHAR(64) NULL;
ALTER TABLE short_links ADD reviewed_at DATETIME NULL;
ALTER TABLE short_links ADD offline_reason VARCHAR(255) NULL;
ALTER TABLE short_links ADD offline_at DATETIME NULL;
ALTER TABLE short_links ADD last_recheck_at DATETIME NULL;
ALTER TABLE short_links MODIFY status ENUM('PENDING','APPROVED','REJECTED','EXPIRED','OFFLINE','TAKEN_DOWN') NOT NULL DEFAULT 'PENDING';
ALTER TABLE short_links ADD KEY idx_short_links_tenant_status (tenant_id, status, created_at);
ALTER TABLE short_links ADD KEY idx_short_links_validity (status, valid_until);

ALTER TABLE short_link_audits ADD audit_action VARCHAR(32) NOT NULL DEFAULT 'AUTO_REVIEW';
ALTER TABLE short_link_audits ADD actor VARCHAR(64) NULL;
ALTER TABLE short_link_audits ADD result_status VARCHAR(32) NULL;
ALTER TABLE short_link_audits ADD evidence_json JSON NULL;
ALTER TABLE short_link_audits ADD created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE short_link_domains (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    domain_name VARCHAR(128) NOT NULL,
    domain_kind ENUM('TARGET','SHORT') NOT NULL,
    filing_status ENUM('APPROVED','PENDING','REJECTED') NOT NULL DEFAULT 'APPROVED',
    domain_age_days INT NOT NULL DEFAULT 365,
    status ENUM('APPROVED','BLACKLISTED','DISABLED') NOT NULL DEFAULT 'APPROVED',
    evidence_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_short_link_domain_kind_name (domain_kind, domain_name),
    KEY idx_short_link_domain_status (domain_kind, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 48 approved target and short-link domains';

CREATE TABLE short_link_click_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    short_link_id BIGINT UNSIGNED NOT NULL,
    visitor_hash CHAR(64) NOT NULL,
    region VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    device_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    clicked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_short_link_visitor_day (short_link_id, visitor_hash, clicked_at),
    KEY idx_short_link_click_link_time (short_link_id, clicked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 48 privacy-safe short-link click events';

INSERT INTO short_link_domains(domain_name, domain_kind, filing_status, domain_age_days, status, evidence_json)
VALUES
  ('example.com', 'TARGET', 'APPROVED', 3650, 'APPROVED', '{"filingNo":"ICP备案-示例","proof":"seed"}'),
  ('s.ycsopen.test', 'SHORT', 'APPROVED', 3650, 'APPROVED', '{"owner":"platform"}'),
  ('blocked.example', 'TARGET', 'REJECTED', 10, 'BLACKLISTED', '{"reason":"domain blacklist"}');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'shortlink:menu', '短链菜单', 'MENU', '/tenant/shortlink', NULL, NULL, 5700, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='shortlink:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'shortlink:read', '查看短链', 'API', '/api/v1/tenant/shortlinks/**', 'GET', NULL, 5701, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='shortlink:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'shortlink:write', '创建短链', 'API', '/api/v1/tenant/shortlinks/**', 'POST', NULL, 5702, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='shortlink:write');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'shortlink-review:menu', '短链审核菜单', 'MENU', '/admin/shortlinks/review', NULL, NULL, 5703, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='shortlink-review:menu');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'shortlink-review:read', '查看短链审核', 'API', '/api/v1/console/shortlinks/review/**', 'GET', NULL, 5704, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='shortlink-review:read');

INSERT INTO permissions(permission_code, permission_name, resource_type, resource_path, http_method, parent_id, sort_order, status)
SELECT 'shortlink-review:write', '审核短链', 'API', '/api/v1/console/shortlinks/review/**', 'POST', NULL, 5705, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_code='shortlink-review:write');
