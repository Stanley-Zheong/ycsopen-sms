package com.ycsopen.sms.core.service.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.web.controller.AdminTenantController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves the Admin wire aggregate supplies the independent token needed by status actions. */
class AdminTenantStatusWorkflowTest {
    private final TenantReviewService reviews = mock(TenantReviewService.class);
    private final TenantMaintenanceService maintenance = mock(TenantMaintenanceService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<QualificationInspectionService> inspections = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<QualificationEvidenceService> evidence = mock(ObjectProvider.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new AdminTenantController(reviews, inspections, evidence, maintenance)).build();
    }

    @Test
    void readStatusStaleRereadAndRetryUsesOnlyAggregateAccountRevision() throws Exception {
        when(reviews.get(42L)).thenReturn(view(4), view(5));
        when(maintenance.changeAccountStatus(42L, 4, TenantAccount.Status.DISABLED,
                "运营停用", "105"))
                .thenThrow(new TenantMaintenanceService.MaintenanceFailure("ACCOUNT_REVISION_STALE"));
        when(maintenance.changeAccountStatus(42L, 5, TenantAccount.Status.DISABLED,
                "运营停用", "105"))
                .thenReturn(new TenantMaintenanceService.AccountStatusResult(
                        42L, TenantAccount.Status.DISABLED, 6));
        var actor = new TestingAuthenticationToken("105", null, "tenant:status:update");

        MvcResult initialRead = mvc.perform(get("/api/v1/console/admin/tenants/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualificationRevision").value(91))
                .andExpect(jsonPath("$.data.accountRevision").value(4))
                .andReturn();
        int firstAccountRevision = data(initialRead).path("accountRevision").intValue();

        mvc.perform(post("/api/v1/console/admin/tenants/42/status")
                        .principal(actor).contentType(MediaType.APPLICATION_JSON)
                        .content(command(firstAccountRevision)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("ACCOUNT_REVISION_STALE"));

        MvcResult refreshedRead = mvc.perform(get("/api/v1/console/admin/tenants/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualificationRevision").value(91))
                .andExpect(jsonPath("$.data.accountRevision").value(5))
                .andReturn();
        int refreshedAccountRevision = data(refreshedRead).path("accountRevision").intValue();

        mvc.perform(post("/api/v1/console/admin/tenants/42/status")
                        .principal(actor).contentType(MediaType.APPLICATION_JSON)
                        .content(command(refreshedAccountRevision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.revision").value(6));

        assertThat(firstAccountRevision).isNotEqualTo(91);
        assertThat(refreshedAccountRevision).isNotEqualTo(firstAccountRevision);
        verify(maintenance).changeAccountStatus(42L, 4, TenantAccount.Status.DISABLED,
                "运营停用", "105");
        verify(maintenance).changeAccountStatus(42L, 5, TenantAccount.Status.DISABLED,
                "运营停用", "105");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private static String command(int accountRevision) {
        return """
                {"expectedRevision":%d,"target":"DISABLED","reason":"运营停用"}
                """.formatted(accountRevision);
    }

    private static TenantReviewService.ReviewView view(int accountRevision) {
        return new TenantReviewService.ReviewView(42L, "T42", "简称", "机构全称",
                "91350211M000100Y46", "法人", "联系人", "100万元", "软件开发",
                "注册地址", "经营地址", 3, "商务经理", "互联网",
                java.time.LocalDate.of(2099, 12, 31), false,
                Tenant.VerificationStatus.VERIFIED, Tenant.LifecycleStatus.TRIAL,
                TenantAccount.Status.NORMAL, accountRevision, 91L,
                java.time.LocalDateTime.of(2026, 9, 7, 8, 0), null,
                Tenant.InspectionStatus.COMPLETED, "机构全称", "91350211M000100Y46",
                0.98, "request-safe", java.time.LocalDateTime.of(2026, 9, 7, 8, 1));
    }
}
