package com.ycsopen.sms.core.service.observability;

import com.ycsopen.sms.core.service.observability.BusinessEventDefinition.DataProtection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEventRegistryTest {

    private static final List<String> PRD_7_1_CODES = List.of(
            "evt_sms_submit",
            "evt_sms_intercept",
            "evt_sms_sent",
            "evt_sms_delivered",
            "evt_sms_failed",
            "evt_signature_submit",
            "evt_template_submit",
            "evt_audit_result",
            "evt_tenant_register",
            "evt_tenant_sign",
            "evt_tenant_terminate",
            "evt_complaint_create",
            "evt_complaint_close",
            "evt_unsubscribe",
            "evt_alert_trigger",
            "evt_shortlink_click",
            "evt_dashboard_view");

    @Test
    void registryCoversEveryPrdBusinessEventExactlyOnce() {
        assertThat(BusinessEventRegistry.codes())
                .containsExactlyElementsOf(PRD_7_1_CODES)
                .doesNotHaveDuplicates();
    }

    @Test
    void everyEventCarriesCorrelationTraceAndTenantContext() {
        for (BusinessEventDefinition event : BusinessEventRegistry.all()) {
            assertThat(event.requiresField("correlationId"))
                    .as(event.code() + " requires correlation identity")
                    .isTrue();
            assertThat(event.requiresField("traceId"))
                    .as(event.code() + " requires trace identity")
                    .isTrue();
            assertThat(event.hasField("tenantId"))
                    .as(event.code() + " carries tenant context, optional only for platform-level events")
                    .isTrue();
        }
    }

    @Test
    void piiFieldsAreMaskedAndRawProtectedValuesAreNotPublic() {
        BusinessEventRegistry.all().stream()
                .flatMap(event -> event.fields().stream())
                .filter(field -> field.name().toLowerCase().contains("phone")
                        || field.name().toLowerCase().contains("ip"))
                .forEach(field -> assertThat(field.protection())
                        .as(field.name() + " must not be public")
                        .isIn(DataProtection.MASKED_PII, DataProtection.PROTECTED_VALUE));

        assertThat(BusinessEventRegistry.require("evt_sms_intercept")
                .requiresField("maskedPhone")).isTrue();
        assertThat(BusinessEventRegistry.require("evt_unsubscribe")
                .requiresField("maskedPhone")).isTrue();
        assertThat(BusinessEventRegistry.require("evt_sms_submit")
                .requiresField("sourceIp")).isTrue();
    }
}
