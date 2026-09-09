package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.tenant.*;
import com.ycsopen.sms.core.web.dto.*;
import com.ycsopen.sms.core.domain.entity.Tenant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TenantRegistrationControllerTest {
    TenantRegistrationService registrations = mock(TenantRegistrationService.class);
    ContactVerificationService contacts = mock(ContactVerificationService.class);
    MockMvc mvc;
    String body = """
        {"qualification":{"shortName":"机构","fullName":"机构有限公司","unifiedSocialCreditCode":"91350211M000100Y46"},
         "contactChallengeId":"challenge", "adminUsername":"initial_admin", "adminPassword":"GoodPassword9!", "adminEmail":"owner@example.com"}
        """;
    @BeforeEach void setup() {
        mvc = standaloneSetup(new TenantRegistrationController(registrations, contacts), new TenantQualificationController(registrations))
                .setControllerAdvice(new TenantQualificationExceptionHandler()).build();
    }

    @Test void publicRegistrationReturnsOnlySafeStateAndNoStore() throws Exception {
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setTenantNo("T42"); tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        when(registrations.register(any(), any(), any(), any(), any(), any())).thenReturn(TenantRegistrationResponse.from(tenant));
        mvc.perform(post("/api/v1/public/tenant-registrations").contentType(MediaType.APPLICATION_JSON).header("X-Registration-Upload-Token", "upload").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.adminPassword").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(value = Tenant.VerificationStatus.class, names = {"REJECTED", "SUPPLEMENT_REQUIRED"})
    void ownStatusJsonIncludesSafeReviewFeedback(Tenant.VerificationStatus state) throws Exception {
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setTenantNo("T42"); tenant.setVerificationStatus(state);
        tenant.setQualificationReason("营业执照照片不清晰，请重新上传。");
        when(registrations.statusOwn("81")).thenReturn(TenantRegistrationResponse.from(tenant));
        var result = mvc.perform(get("/api/v1/console/tenant/qualification").principal(() -> "81"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.reason").value("营业执照照片不清晰，请重新上传。"))
                .andExpect(jsonPath("$.data.verificationStatus").value(state.name())).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("contactPhone", "legalRepId", "ObjectId", "pobj_", "uploadToken", "locator", "codeHash", "password", "credentials");
    }

    @Test void databaseRaceIs409WithSafeCategory() throws Exception {
        when(registrations.register(any(), any(), any(), any(), any(), any())).thenThrow(new TenantRegistrationService.SubmissionFailure("DUPLICATE_REGISTRATION"));
        mvc.perform(post("/api/v1/public/tenant-registrations").contentType(MediaType.APPLICATION_JSON).header("X-Registration-Upload-Token", "upload").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REGISTRATION"));
    }

    @Test void deliveryFailureIs503AndNeverReturnsCode() throws Exception {
        doThrow(new TenantRegistrationService.SubmissionFailure("CONTACT_VERIFICATION_DELIVERY_UNAVAILABLE"))
                .when(contacts).request(any(), any());
        mvc.perform(post("/api/v1/public/tenant-registrations/contact-challenges").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"13800138000\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONTACT_VERIFICATION_DELIVERY_UNAVAILABLE"));
    }

    @Test void rejectsUnknownTenantIdentityAtTheRequestBoundary() throws Exception {
        mvc.perform(post("/api/v1/public/tenant-registrations").contentType(MediaType.APPLICATION_JSON).content(body.replace("\"qualification\"", "\"tenantId\":777,\"qualification\"")))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(registrations);
    }

    @Test void ownStatusUsesAuthenticatedSubject() throws Exception {
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setTenantNo("T42");
        tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        when(registrations.statusOwn("81")).thenReturn(TenantRegistrationResponse.from(tenant));
        mvc.perform(get("/api/v1/console/tenant/qualification").principal(() -> "81"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantId").value(42))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.nullValue()));
    }
}
