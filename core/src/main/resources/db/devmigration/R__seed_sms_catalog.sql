-- Local development catalog: mock CMPP channels, demo tenants/templates and
-- a small lawful demonstration prefix dataset.  All statements are idempotent.
INSERT INTO tenants (tenant_no, short_name, full_name, unified_social_credit_code,
                     contact_name, contact_phone_encrypted, verification_status,
                     lifecycle_status, trial_quota, trial_start_at, trial_end_at)
SELECT 'DEMO-001','演示机构','演示短信机构','91110000MA00000001','演示管理员',X'00',
       'VERIFIED','TRIAL',500,CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 14 DAY)
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE tenant_no='DEMO-001');

INSERT INTO tenant_accounts (tenant_id, balance)
SELECT id,100000000 FROM tenants WHERE tenant_no='DEMO-001'
  AND NOT EXISTS (SELECT 1 FROM tenant_accounts a WHERE a.tenant_id=tenants.id);

INSERT INTO signatures (tenant_id, sign_code, sign_content, sign_type, audit_status, audit_time)
SELECT id,'DEMO','【演示机构】','ENTERPRISE','APPROVED',CURRENT_TIMESTAMP
FROM tenants WHERE tenant_no='DEMO-001'
  AND NOT EXISTS (SELECT 1 FROM signatures s JOIN tenants t ON t.id=s.tenant_id WHERE t.tenant_no='DEMO-001' AND s.sign_code='DEMO');

INSERT INTO templates (tenant_id, template_code, template_name, template_type, content, signature_id, audit_status, audit_time)
SELECT t.id,'DEMO_LOGIN','登录验证码','VERIFY','您的验证码是$${code}，5分钟内有效。',s.id,'APPROVED',CURRENT_TIMESTAMP
FROM tenants t JOIN signatures s ON s.tenant_id=t.id AND s.sign_code='DEMO'
WHERE t.tenant_no='DEMO-001' AND NOT EXISTS (SELECT 1 FROM templates x WHERE x.tenant_id=t.id AND x.template_code='DEMO_LOGIN');
INSERT INTO templates (tenant_id, template_code, template_name, template_type, content, signature_id, audit_status, audit_time)
SELECT t.id,'DEMO_NOTICE','账户变动通知','NOTIFY','您的账户于$${time}发生$${action}，余额$${balance}。',s.id,'APPROVED',CURRENT_TIMESTAMP
FROM tenants t JOIN signatures s ON s.tenant_id=t.id AND s.sign_code='DEMO'
WHERE t.tenant_no='DEMO-001' AND NOT EXISTS (SELECT 1 FROM templates x WHERE x.tenant_id=t.id AND x.template_code='DEMO_NOTICE');
INSERT INTO templates (tenant_id, template_code, template_name, template_type, content, signature_id, audit_status, audit_time)
SELECT t.id,'DEMO_MARKETING','活动营销通知','MARKETING','【演示机构】$${campaign}，详情请访问$${url}。',s.id,'APPROVED',CURRENT_TIMESTAMP
FROM tenants t JOIN signatures s ON s.tenant_id=t.id AND s.sign_code='DEMO'
WHERE t.tenant_no='DEMO-001' AND NOT EXISTS (SELECT 1 FROM templates x WHERE x.tenant_id=t.id AND x.template_code='DEMO_MARKETING');

INSERT INTO channels (channel_name, protocol, operator, host, port, sp_id, service_id, src_id, price, priority, status, availability, effective_version_id, configuration_version, extra_config)
SELECT v.name,'CMPP',v.operator,'mock-cmpp',7890,'MOCK-SP','SMS','1069',v.price,v.priority,'NORMAL','AVAILABLE',NULL,0,'{"mock":true,"provider":"local"}'
FROM (SELECT 'A移动' name,'MOBILE' operator,0.0270 price,10 priority UNION ALL SELECT 'B联通','UNICOM',0.0278,20 UNION ALL SELECT 'C移动','MOBILE',0.0286,30 UNION ALL SELECT 'D电信','TELECOM',0.0294,40 UNION ALL SELECT 'E联通','UNICOM',0.0301,50 UNION ALL SELECT 'F移动','MOBILE',0.0308,60 UNION ALL SELECT 'G电信','TELECOM',0.0316,70 UNION ALL SELECT 'H移动','MOBILE',0.0324,80 UNION ALL SELECT 'I移动','MOBILE',0.0332,90 UNION ALL SELECT '同业广联A','VIRTUAL',0.0289,25 UNION ALL SELECT '同业财源B','VIRTUAL',0.0305,35 UNION ALL SELECT '同业营销C','VIRTUAL',0.0317,45 UNION ALL SELECT '同业竞争D','VIRTUAL',0.0328,55) v
WHERE NOT EXISTS (SELECT 1 FROM channels c WHERE c.channel_name=v.name);

INSERT INTO channel_configuration_versions (channel_id,payload_json,status,reason_code)
SELECT c.id,JSON_OBJECT('protocol','CMPP','mock',true,'price',c.price),'EFFECTIVE','DEV_SEED'
FROM channels c WHERE c.host='mock-cmpp' AND c.effective_version_id IS NULL;
UPDATE channels c JOIN (SELECT channel_id,MAX(id) id FROM channel_configuration_versions WHERE reason_code='DEV_SEED' GROUP BY channel_id) v ON v.channel_id=c.id
SET c.effective_version_id=v.id,c.configuration_version=1;

INSERT INTO tenant_price_books (price_book_version,product_code,unit_price_mil,tier_rule_json,status)
SELECT 'SMS_DEMO_MOBILE','SMS',270,JSON_OBJECT('operator','MOBILE','threshold',10000000,'discountMil',20),'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM tenant_price_books WHERE price_book_version='SMS_DEMO_MOBILE');
INSERT INTO tenant_price_books (price_book_version,product_code,unit_price_mil,tier_rule_json,status)
SELECT 'SMS_DEMO_UNICOM','SMS',278,JSON_OBJECT('operator','UNICOM','threshold',10000000,'discountMil',20),'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM tenant_price_books WHERE price_book_version='SMS_DEMO_UNICOM');
INSERT INTO tenant_price_books (price_book_version,product_code,unit_price_mil,tier_rule_json,status)
SELECT 'SMS_DEMO_TELECOM','SMS',294,JSON_OBJECT('operator','TELECOM','threshold',10000000,'discountMil',20),'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM tenant_price_books WHERE price_book_version='SMS_DEMO_TELECOM');

INSERT INTO number_prefix_versions(version_no,update_type,status,source_name,total_rows,actor,activated_at)
SELECT 'CN-MOCK-2026-01','FULL','ACTIVE','public-demo-prefix-sample',6,'flyway-dev',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM number_prefix_versions WHERE version_no='CN-MOCK-2026-01');
INSERT INTO number_prefix_mappings(version_id,prefix,carrier,province,city,source_name)
SELECT v.id,x.prefix,x.carrier,x.province,x.city,'public-demo-prefix-sample'
FROM number_prefix_versions v JOIN (SELECT '1380013' prefix,'MOBILE' carrier,'北京' province,'北京' city UNION ALL SELECT '1860013','UNICOM','北京','北京' UNION ALL SELECT '1330013','TELECOM','北京','北京' UNION ALL SELECT '1390000','MOBILE','上海','上海' UNION ALL SELECT '1860210','UNICOM','上海','上海' UNION ALL SELECT '1890210','TELECOM','上海','上海') x
WHERE v.version_no='CN-MOCK-2026-01' AND NOT EXISTS (SELECT 1 FROM number_prefix_mappings m WHERE m.version_id=v.id AND m.prefix=x.prefix);
