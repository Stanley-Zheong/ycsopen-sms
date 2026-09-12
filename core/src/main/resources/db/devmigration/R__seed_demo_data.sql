-- Local-only demo records for smoke testing the console and tenant flows.
-- The repeatable migration is idempotent and is never loaded outside profile=dev.
INSERT INTO tenants (
    tenant_no, short_name, full_name, unified_social_credit_code,
    contact_name, registered_capital, business_scope, registered_address,
    business_address, customer_level, verification_status, lifecycle_status,
    trial_quota, trial_quota_used, trial_start_at, trial_end_at, created_by
)
SELECT 'DEMO-001', '演示机构', '优创硕安演示机构', '91110000MA0000000X',
       '演示联系人', '1000000', '短信平台联调演示', '北京市海淀区',
       '北京市海淀区', 1, 'VERIFIED', 'TRIAL', 500, 0,
       CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 14 DAY), 'flyway-dev'
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE tenant_no = 'DEMO-001');

INSERT INTO tenant_accounts (tenant_id, balance, frozen_amount, status)
SELECT id, 100000, 0, 'NORMAL'
FROM tenants
WHERE tenant_no = 'DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM tenant_accounts account
      WHERE account.tenant_id = tenants.id
  );

INSERT INTO signatures (
    tenant_id, biz_type, sign_code, sign_content, sign_type,
    usage_type, risk_level, applicant_name, audit_status, audit_time
)
SELECT id, 'DOMESTIC', 'DEMO', '【演示机构】', 'ENTERPRISE',
       'SELF', 'LOW', '演示联系人', 'APPROVED', CURRENT_TIMESTAMP
FROM tenants
WHERE tenant_no = 'DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM signatures signature_row
      WHERE signature_row.tenant_id = tenants.id AND signature_row.sign_code = 'DEMO'
  );

INSERT INTO templates (
    tenant_id, biz_type, template_code, template_name, template_type,
    content, signature_id, param_check_rule, audit_status, audit_time
)
SELECT tenant.id, 'DOMESTIC', 'DEMO_LOGIN', '演示验证码', 'VERIFY',
       '您的验证码是$${code}，5分钟内有效。', signature_row.id,
       '{"code":"^[0-9]{6}$"}', 'APPROVED', CURRENT_TIMESTAMP
FROM tenants tenant
JOIN signatures signature_row
  ON signature_row.tenant_id = tenant.id AND signature_row.sign_code = 'DEMO'
WHERE tenant.tenant_no = 'DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM templates template_row
      WHERE template_row.tenant_id = tenant.id AND template_row.template_code = 'DEMO_LOGIN'
  );

INSERT INTO channels (
    channel_name, protocol, operator, host, port, sp_id, service_id,
    src_id, max_connections, window_size, price, priority, status
)
SELECT '本地 Sandbox HTTP', 'HTTP', 'VIRTUAL', 'sandbox', 8081,
       'DEMO', 'demo', '0000', 2, 8, 0.0500, 1, 'NORMAL'
WHERE NOT EXISTS (SELECT 1 FROM channels WHERE channel_name = '本地 Sandbox HTTP');
