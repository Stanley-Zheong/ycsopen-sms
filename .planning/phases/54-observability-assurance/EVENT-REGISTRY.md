# Phase 54 Event Registry

The executable source of truth is `core/src/main/java/com/ycsopen/sms/core/service/observability/BusinessEventRegistry.java`.

| Code | PRD event |
| --- | --- |
| evt_sms_submit | 短信提交事件 |
| evt_sms_intercept | 提交拦截事件 |
| evt_sms_sent | 发送成功事件 |
| evt_sms_delivered | 送达成功事件 |
| evt_sms_failed | 送达失败事件 |
| evt_signature_submit | 签名提交事件 |
| evt_template_submit | 模板提交事件 |
| evt_audit_result | 审核结果事件 |
| evt_tenant_register | 机构注册事件 |
| evt_tenant_sign | 机构签约事件 |
| evt_tenant_terminate | 机构终止事件 |
| evt_complaint_create | 投诉登记事件 |
| evt_complaint_close | 投诉关闭事件 |
| evt_unsubscribe | 退订事件 |
| evt_alert_trigger | 告警触发事件 |
| evt_shortlink_click | 短链点击事件 |
| evt_dashboard_view | 仪表盘访问事件 |

Every registry entry is required to carry `correlationId`, `traceId`, and a `tenantId` field. Sensitive phone fields are represented as `maskedPhone`; source IP is classified as `PROTECTED_VALUE`.
