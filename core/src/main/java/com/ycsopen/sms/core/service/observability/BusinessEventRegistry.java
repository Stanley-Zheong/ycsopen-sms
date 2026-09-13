package com.ycsopen.sms.core.service.observability;

import com.ycsopen.sms.core.service.observability.BusinessEventDefinition.DataProtection;
import com.ycsopen.sms.core.service.observability.BusinessEventDefinition.EventField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central registry for the PRD 7.1 observable business-event contract. */
public final class BusinessEventRegistry {

    private static final List<BusinessEventDefinition> EVENTS = List.of(
            event("evt_sms_submit", "短信提交事件", "下游 HTTP/CMPP 提交请求进入网关时",
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("sourceProtocol", DataProtection.PUBLIC),
                    required("productType", DataProtection.PUBLIC),
                    required("templateId", DataProtection.INTERNAL_ID),
                    required("requestTime", DataProtection.PUBLIC),
                    required("sourceIp", DataProtection.PROTECTED_VALUE),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_sms_intercept", "提交拦截事件", "路由前置检测命中黑名单、内容审核或频控时",
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("interceptType", DataProtection.PUBLIC),
                    required("interceptSource", DataProtection.PUBLIC),
                    required("maskedPhone", DataProtection.MASKED_PII),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_sms_sent", "发送成功事件", "通道连接器收到上游 SUBMIT_RESP 成功响应时",
                    required("messageId", DataProtection.INTERNAL_ID),
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("channelId", DataProtection.INTERNAL_ID),
                    required("carrier", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_sms_delivered", "送达成功事件", "收到上游成功状态报告时",
                    required("messageId", DataProtection.INTERNAL_ID),
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("status", DataProtection.PUBLIC),
                    optional("errorCode", DataProtection.PUBLIC),
                    required("latencyMillis", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_sms_failed", "送达失败事件", "收到上游失败状态报告时",
                    required("messageId", DataProtection.INTERNAL_ID),
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("status", DataProtection.PUBLIC),
                    required("errorCode", DataProtection.PUBLIC),
                    required("latencyMillis", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_signature_submit", "签名提交事件", "机构提交签名申请时",
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("signatureId", DataProtection.INTERNAL_ID),
                    required("type", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_template_submit", "模板提交事件", "机构提交模板申请时",
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("templateId", DataProtection.INTERNAL_ID),
                    required("type", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_audit_result", "审核结果事件", "运营完成签名、模板或短链审核操作时",
                    optional("tenantId", DataProtection.INTERNAL_ID),
                    required("subjectType", DataProtection.PUBLIC),
                    required("reviewResult", DataProtection.PUBLIC),
                    required("reviewerUserId", DataProtection.INTERNAL_ID),
                    required("processingMillis", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_tenant_register", "机构注册事件", "机构完成注册生命周期节点时",
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("eventType", DataProtection.PUBLIC),
                    required("operatorUserId", DataProtection.INTERNAL_ID),
                    required("timestamp", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_tenant_sign", "机构签约事件", "机构完成签约生命周期节点时",
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("eventType", DataProtection.PUBLIC),
                    required("operatorUserId", DataProtection.INTERNAL_ID),
                    required("timestamp", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_tenant_terminate", "机构终止事件", "机构完成终止生命周期节点时",
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("eventType", DataProtection.PUBLIC),
                    required("operatorUserId", DataProtection.INTERNAL_ID),
                    required("timestamp", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_complaint_create", "投诉登记事件", "投诉工单创建时",
                    required("ticketId", DataProtection.INTERNAL_ID),
                    required("tenantId", DataProtection.INTERNAL_ID),
                    optional("channelId", DataProtection.INTERNAL_ID),
                    optional("signatureId", DataProtection.INTERNAL_ID),
                    required("complaintSource", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_complaint_close", "投诉关闭事件", "投诉工单关闭时",
                    required("ticketId", DataProtection.INTERNAL_ID),
                    required("tenantId", DataProtection.INTERNAL_ID),
                    optional("channelId", DataProtection.INTERNAL_ID),
                    optional("signatureId", DataProtection.INTERNAL_ID),
                    required("complaintSource", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_unsubscribe", "退订事件", "上行消息命中退订关键词时",
                    required("maskedPhone", DataProtection.MASKED_PII),
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("signatureId", DataProtection.INTERNAL_ID),
                    required("unsubscribeMethod", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_alert_trigger", "告警触发事件", "任一告警规则命中阈值时",
                    required("alertRuleId", DataProtection.INTERNAL_ID),
                    optional("tenantId", DataProtection.INTERNAL_ID),
                    required("alertType", DataProtection.PUBLIC),
                    required("currentMetricValue", DataProtection.PUBLIC),
                    required("severity", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_shortlink_click", "短链点击事件", "终端用户点击短链跳转时",
                    required("shortLinkId", DataProtection.INTERNAL_ID),
                    required("tenantId", DataProtection.INTERNAL_ID),
                    required("clickTime", DataProtection.PUBLIC),
                    required("region", DataProtection.PUBLIC),
                    required("terminalType", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC)),
            event("evt_dashboard_view", "仪表盘访问事件", "用户打开任一仪表盘页面时",
                    optional("tenantId", DataProtection.INTERNAL_ID),
                    required("userId", DataProtection.INTERNAL_ID),
                    required("role", DataProtection.PUBLIC),
                    required("dashboardType", DataProtection.PUBLIC),
                    required("dwellMillis", DataProtection.PUBLIC),
                    required("correlationId", DataProtection.PUBLIC),
                    required("traceId", DataProtection.PUBLIC))
    );

    private static final Map<String, BusinessEventDefinition> BY_CODE = byCode();

    private BusinessEventRegistry() {
    }

    public static List<BusinessEventDefinition> all() {
        return EVENTS;
    }

    public static BusinessEventDefinition require(String code) {
        BusinessEventDefinition event = BY_CODE.get(code);
        if (event == null) {
            throw new IllegalArgumentException("unknown business event code: " + code);
        }
        return event;
    }

    public static List<String> codes() {
        return EVENTS.stream().map(BusinessEventDefinition::code).toList();
    }

    private static Map<String, BusinessEventDefinition> byCode() {
        Map<String, BusinessEventDefinition> events = new LinkedHashMap<>();
        for (BusinessEventDefinition event : EVENTS) {
            if (events.put(event.code(), event) != null) {
                throw new IllegalStateException("duplicate business event code: " + event.code());
            }
        }
        return Map.copyOf(events);
    }

    private static BusinessEventDefinition event(String code, String name, String trigger, EventField... fields) {
        return new BusinessEventDefinition(code, name, trigger, List.of(fields));
    }

    private static EventField required(String name, DataProtection protection) {
        return new EventField(name, true, protection);
    }

    private static EventField optional(String name, DataProtection protection) {
        return new EventField(name, false, protection);
    }
}
