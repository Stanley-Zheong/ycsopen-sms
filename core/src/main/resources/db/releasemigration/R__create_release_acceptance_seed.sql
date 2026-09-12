-- Issue #60 development-release fixtures. This repeatable migration is loaded
-- from the release-only location by the dev profile. Every insert is additive,
-- so an upgrade preserves operator-managed rows and a checksum change cannot
-- create duplicates.

INSERT INTO roles (role_code, role_name, description, role_type, tenant_id, status)
SELECT 'SYSTEM_ADMIN', '系统管理员', '开发发布验收管理员', 'PLATFORM', NULL, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_code = 'SYSTEM_ADMIN');

INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT u.id, r.id, 'flyway-dev'
FROM users u
JOIN roles r ON r.role_code = 'SYSTEM_ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.status = 'ACTIVE'
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO tenants (
    tenant_no, short_name, full_name, unified_social_credit_code,
    verification_status, lifecycle_status, created_by
)
SELECT
    'DEV-TENANT', '发布验收', '发布验收开发机构', '91110108MA00000060',
    'VERIFIED', 'SIGNED', 'flyway-dev'
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE tenant_no = 'DEV-TENANT');

INSERT INTO signatures (
    tenant_id, biz_type, sign_code, sign_content, sign_type, usage_type,
    risk_level, audit_status, audit_time, audit_comment
)
SELECT
    t.id, 'DOMESTIC', 'DEV-SIGN', '发布验收', 'ENTERPRISE', 'SELF',
    'LOW', 'APPROVED', CURRENT_TIMESTAMP, '开发发布验收种子'
FROM tenants t
WHERE t.tenant_no = 'DEV-TENANT'
  AND NOT EXISTS (
      SELECT 1 FROM signatures s WHERE s.tenant_id = t.id AND s.sign_code = 'DEV-SIGN'
  );

INSERT INTO templates (
    tenant_id, biz_type, template_code, template_name, template_type,
    content, signature_id, description, audit_status, audit_time,
    audit_comment, is_system_template
)
SELECT
    t.id, 'DOMESTIC', 'DEV-VERIFY-CODE', '开发验证码', 'VERIFY',
    '您的验证码是 ${code}', s.id, '开发发布验收种子', 'APPROVED',
    CURRENT_TIMESTAMP, '开发发布验收种子', 0
FROM tenants t
JOIN signatures s ON s.tenant_id = t.id AND s.sign_code = 'DEV-SIGN'
WHERE t.tenant_no = 'DEV-TENANT'
  AND NOT EXISTS (
      SELECT 1 FROM templates tpl
      WHERE tpl.tenant_id = t.id AND tpl.template_code = 'DEV-VERIFY-CODE'
  );

INSERT INTO channels (
    channel_name, protocol, operator, host, port, sp_id, service_id,
    src_id, max_connections, window_size, price, priority,
    active_window, extra_config, status
)
SELECT
    'DEV-CMPP-PRIMARY', 'CMPP', 'MOBILE', '127.0.0.1', 7890,
    'DEVSP', 'DEVVERIFY', '10690000', 2, 8, 0.0500, 50,
    '00:00-23:59', '{"releaseFixture":true}', 'NORMAL'
WHERE NOT EXISTS (SELECT 1 FROM channels WHERE channel_name = 'DEV-CMPP-PRIMARY');

INSERT INTO number_prefix_versions (
    version_no, update_type, status, source_name, total_rows,
    conflict_count, actor, activated_at
)
SELECT
    'DEV-PREFIX-2026-09', 'FULL', 'ACTIVE', 'release-seed', 1,
    0, 'flyway-dev', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM number_prefix_versions WHERE version_no = 'DEV-PREFIX-2026-09'
);

INSERT INTO number_prefix_mappings (
    version_id, prefix, carrier, province, city, source_name, status
)
SELECT
    v.id, '1380013', 'MOBILE', '北京', '北京', 'release-seed', 'ACTIVE'
FROM number_prefix_versions v
WHERE v.version_no = 'DEV-PREFIX-2026-09'
  AND NOT EXISTS (
      SELECT 1 FROM number_prefix_mappings m
      WHERE m.version_id = v.id AND m.prefix = '1380013'
  );

-- Demo CMPP channels used by local acceptance and manual verification.  The
-- repeatable migration is additive and never overwrites operator-managed rows.
INSERT INTO channels (
    channel_name, protocol, operator, host, port, sp_id, service_id, src_id,
    max_connections, window_size, price, priority, active_window, extra_config, status
)
SELECT seed.channel_name, 'CMPP', seed.operator, 'mock-cmpp', 7890,
       'MOCK-SP', 'MOCK-SMS', '10690000', 4, 16, seed.price, seed.priority,
       '00:00-23:59', JSON_OBJECT('mock', TRUE, 'releaseFixture', TRUE), 'NORMAL'
FROM (
    SELECT 'A移动' channel_name, 'MOBILE' operator, 0.0270 price, 10 priority UNION ALL
    SELECT 'B联通', 'UNICOM', 0.0278, 20 UNION ALL
    SELECT 'C移动', 'MOBILE', 0.0285, 30 UNION ALL
    SELECT 'D电信', 'TELECOM', 0.0292, 40 UNION ALL
    SELECT 'E联通', 'UNICOM', 0.0299, 50 UNION ALL
    SELECT 'F移动', 'MOBILE', 0.0304, 60 UNION ALL
    SELECT 'G电信', 'TELECOM', 0.0310, 70 UNION ALL
    SELECT 'H移动', 'MOBILE', 0.0316, 80 UNION ALL
    SELECT 'I移动', 'MOBILE', 0.0322, 90 UNION ALL
    SELECT '同业广联A', 'VIRTUAL', 0.0325, 100 UNION ALL
    SELECT '同业财源B', 'VIRTUAL', 0.0327, 110 UNION ALL
    SELECT '同业营销C', 'VIRTUAL', 0.0329, 120 UNION ALL
    SELECT '同业竞争D', 'VIRTUAL', 0.0330, 130
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM channels existing WHERE existing.channel_name = seed.channel_name
);

-- Three representative operator price books with a 10-million-message tier
-- discount of 0.002 yuan, retained as JSON for the existing pricing model.
INSERT INTO tenant_price_books (
    price_book_version, product_code, unit_price_mil, tier_rule_json, status
)
SELECT seed.version, 'SMS', seed.unit_price_mil,
       JSON_OBJECT('thresholdMessages', 10000000, 'discountMil', 2), 'ACTIVE'
FROM (
    SELECT 'SMS_MOBILE_V2' version, 27 unit_price_mil UNION ALL
    SELECT 'SMS_UNICOM_V2', 28 UNION ALL
    SELECT 'SMS_TELECOM_V2', 29
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_price_books existing
    WHERE existing.price_book_version = seed.version
);

INSERT INTO templates (
    tenant_id, biz_type, template_code, template_name, template_type,
    content, signature_id, description, audit_status, audit_time,
    audit_comment, is_system_template
)
SELECT t.id, 'DOMESTIC', seed.template_code, seed.template_name, seed.template_type,
       seed.content, s.id, '本地验收测试模板', 'APPROVED', CURRENT_TIMESTAMP,
       'release seed', 0
FROM tenants t
JOIN signatures s ON s.tenant_id = t.id AND s.sign_code = 'DEV-SIGN'
JOIN (
    SELECT 'DEV-NOTIFY' template_code, '开发通知' template_name, 'NOTIFY' template_type,
           '您有一条新的服务通知' content UNION ALL
    SELECT 'DEV-MARKETING', '开发营销', 'MARKETING', '限时优惠活动，回复TD退订'
) seed
WHERE t.tenant_no = 'DEV-TENANT'
  AND NOT EXISTS (
      SELECT 1 FROM templates existing
      WHERE existing.tenant_id = t.id AND existing.template_code = seed.template_code
  );
