package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.service.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Legacy console routes remain addressable but cannot bypass qualification or review contracts. */
class TenantControllerCompatibilityTest {
    private final TenantService tenants = mock(TenantService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new TenantController(tenants)).build();

    @Test
    void legacyRegistrationNeverCreatesTenant() throws Exception {
        mvc.perform(post("/api/v1/console/tenants/register")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LEGACY_REGISTRATION_ROUTE_REMOVED"));
        verify(tenants, never()).submitRegistration(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullRegistrationRequestReturnsStableMigrationError() {
        assertThatThrownBy(() -> new TenantController(tenants).register(null, null))
                .isInstanceOf(TenantController.LegacyRouteException.class)
                .hasMessage("LEGACY_REGISTRATION_ROUTE_REMOVED");
    }

    @Test
    void legacyApprovalAndRejectionReturnStableMigrationErrors() throws Exception {
        mvc.perform(post("/api/v1/console/tenants/7/approve-and-activate-trial"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LEGACY_APPROVAL_REQUIRES_REVIEW"));
        mvc.perform(post("/api/v1/console/tenants/7/reject"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LEGACY_REJECTION_REQUIRES_REVIEW"));
    }

    @Test
    void legacyObjectUrlStillGetsSpecificSafeErrorWithoutServiceCall() throws Exception {
        mvc.perform(post("/api/v1/console/tenants/register")
                        .header(TenantRegistrationProtectionAdapter.UPLOAD_TOKEN_HEADER, "ignored")
                        .contentType("application/json")
                        .content("{\"businessLicenseUrl\":\"https://storage.invalid/raw\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LEGACY_OBJECT_URL_NOT_ACCEPTED"));
        verify(tenants, never()).submitRegistration(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
