package com.ycsopen.sms.core.service.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.controller.TenantQualificationController;
import com.ycsopen.sms.core.web.dto.TenantRegistrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TenantQualificationSafeAggregateTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TenantRegistrationService registrations = new TenantRegistrationService(tenants, users,
            mock(TenantService.class), mock(TenantRegistrationProtectionAdapter.class),
            mock(ContactVerificationService.class), new BCryptPasswordEncoder());
    private MockMvc mvc;

    @BeforeEach void ownIdentity() {
        User actor = new User(); actor.setId(81L); actor.setTenantId(42L); actor.setUserType(User.UserType.TENANT_ADMIN);
        when(users.findById(81L)).thenReturn(Optional.of(actor));
        mvc = standaloneSetup(new TenantQualificationController(registrations)).build();
    }

    @ParameterizedTest
    @EnumSource(value = Tenant.VerificationStatus.class, names = {"PENDING", "VERIFIED"})
    void ownStatusAndMvcReturnSubmittedSafeValuesAndTruthfulMaterialPresence(Tenant.VerificationStatus state) throws Exception {
        Tenant tenant = submitted(state);
        ReflectionTestUtils.setField(tenant, "businessLicenseObjectId", "pobj_v1_business_private");
        ReflectionTestUtils.setField(tenant, "legalRepIdFrontObjectId", "pobj_v1_front_private");
        ReflectionTestUtils.setField(tenant, "legalRepIdBackObjectId", "pobj_v1_back_private");
        ReflectionTestUtils.setField(tenant, "legalRepIdNoEncrypted", "identity-envelope-canary".getBytes(StandardCharsets.US_ASCII));
        ReflectionTestUtils.setField(tenant, "contactIdNoEncrypted", "contact-envelope-canary".getBytes(StandardCharsets.US_ASCII));
        ReflectionTestUtils.setField(tenant, "contactPhoneEncrypted", "phone-envelope-canary".getBytes(StandardCharsets.US_ASCII));
        when(tenants.findById(42L)).thenReturn(Optional.of(tenant));
        assertSubmittedAggregate(json.valueToTree(registrations.statusOwn("81")));
        var response = mvc.perform(get("/api/v1/console/tenant/qualification").principal(() -> "81"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.verificationStatus").value(state.name())).andReturn();
        JsonNode data = json.readTree(response.getResponse().getContentAsByteArray()).path("data");
        assertSubmittedAggregate(data);
    }

    @Test void absenceAndOptionalPresenceAreDerivedFromStoredMaterialNotFromStatusOrTrademarkIntent() {
        Tenant tenant = submitted(Tenant.VerificationStatus.VERIFIED);
        tenant.setTrademarkUse(true);
        ReflectionTestUtils.setField(tenant, "businessLicenseObjectId", " ");
        ReflectionTestUtils.setField(tenant, "contactPhoneEncrypted", new byte[0]);
        ReflectionTestUtils.setField(tenant, "shortlinkDomainProofObjectId", "pobj_v1_shortlink_private");
        ReflectionTestUtils.setField(tenant, "trademarkProofObjectId", "pobj_v1_trademark_private");
        when(tenants.findById(42L)).thenReturn(Optional.of(tenant));
        JsonNode data = json.valueToTree(registrations.statusOwn("81"));
        for (String field : new String[]{"businessLicensePresent", "legalRepresentativeIdentityPresent", "legalRepresentativeIdFrontPresent",
                "legalRepresentativeIdBackPresent", "contactIdentityPresent", "contactPhonePresent"}) {
            assertThat(data.has(field)).as(field).isTrue();
            assertThat(data.path(field).booleanValue()).as(field).isFalse();
        }
        assertThat(data.path("shortlinkProofPresent").booleanValue()).isTrue();
        assertThat(data.path("trademarkProofPresent").booleanValue()).isTrue();
        assertNoProtectedData(data);
    }

    @Test void publicAndLegacyResponsesKeepTheirStatusAndReasonWithoutAddingMaterialClaims() {
        var legacy = new TenantRegistrationResponse(42L, "T42", "机构", "机构有限公司", Tenant.VerificationStatus.PENDING,
                Tenant.LifecycleStatus.SUBMITTED, null, null, null, 2);
        var withReason = new TenantRegistrationResponse(42L, "T42", "机构", "机构有限公司", Tenant.VerificationStatus.REJECTED,
                Tenant.LifecycleStatus.SUBMITTED, null, null, null, 3, "请补充营业执照。");
        for (var response : new TenantRegistrationResponse[]{legacy, withReason, TenantRegistrationResponse.from(submitted(Tenant.VerificationStatus.PENDING))}) {
            JsonNode data = json.valueToTree(response);
            assertThat(data.size()).isEqualTo(11);
            assertThat(data.has("businessLicensePresent")).isFalse();
            assertThat(data.has("unifiedSocialCreditCode")).isFalse();
        }
        assertThat(legacy.reason()).isNull();
        assertThat(withReason.reason()).isEqualTo("请补充营业执照。");
    }

    private static Tenant submitted(Tenant.VerificationStatus state) {
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setTenantNo("T42"); tenant.setShortName("示例机构"); tenant.setFullName("示例机构有限公司");
        tenant.setVerificationStatus(state); tenant.setUnifiedSocialCreditCode("91350211M000100Y46");
        tenant.setLegalRepName("张三"); tenant.setContactName("李四"); tenant.setRegisteredCapital("100万元人民币");
        tenant.setBusinessScope("软件开发"); tenant.setRegisteredAddress("上海市注册地址1号"); tenant.setBusinessAddress("上海市经营地址2号");
        tenant.setLicenseValidUntil(LocalDate.of(2099, 12, 31)); tenant.setTrademarkUse(false);
        return tenant;
    }

    private static void assertSubmittedAggregate(JsonNode data) {
        assertThat(data.path("unifiedSocialCreditCode").asText()).isEqualTo("91350211M000100Y46");
        assertThat(data.path("legalRepresentativeName").asText()).isEqualTo("张三");
        assertThat(data.path("contactName").asText()).isEqualTo("李四");
        assertThat(data.path("registeredCapital").asText()).isEqualTo("100万元人民币");
        assertThat(data.path("businessScope").asText()).isEqualTo("软件开发");
        assertThat(data.path("registeredAddress").asText()).isEqualTo("上海市注册地址1号");
        assertThat(data.path("businessAddress").asText()).isEqualTo("上海市经营地址2号");
        assertThat(data.path("licenseValidUntil").asText()).isEqualTo("2099-12-31");
        assertThat(data.path("trademarkUse").isBoolean()).isTrue();
        assertThat(data.path("trademarkUse").booleanValue()).isFalse();
        for (String field : new String[]{"businessLicensePresent", "legalRepresentativeIdentityPresent", "legalRepresentativeIdFrontPresent",
                "legalRepresentativeIdBackPresent", "contactIdentityPresent", "contactPhonePresent"}) {
            assertThat(data.path(field).booleanValue()).as(field).isTrue();
        }
        for (String field : new String[]{"shortlinkProofPresent", "trademarkProofPresent"}) {
            assertThat(data.has(field)).as(field).isTrue();
            assertThat(data.path(field).booleanValue()).as(field).isFalse();
        }
        assertNoProtectedData(data);
    }

    private static void assertNoProtectedData(JsonNode data) {
        Set<String> fields = new HashSet<>(); data.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("tenantId", "tenantNo", "shortName", "fullName", "verificationStatus", "lifecycleStatus",
                "submittedAt", "verifiedAt", "verificationUpdatedAt", "revision", "reason", "unifiedSocialCreditCode", "legalRepresentativeName",
                "contactName", "registeredCapital", "businessScope", "registeredAddress", "businessAddress", "licenseValidUntil", "trademarkUse",
                "businessLicensePresent", "legalRepresentativeIdentityPresent", "legalRepresentativeIdFrontPresent", "legalRepresentativeIdBackPresent",
                "contactIdentityPresent", "contactPhonePresent", "shortlinkProofPresent", "trademarkProofPresent");
        assertThat(data.toString()).doesNotContain("pobj_", "envelope-canary", "11010519491231002X", "13800138000", "Encrypted", "ObjectId", "legalRepIdNo", "contactIdNo",
                "uploadToken", "capability", "locator", "codeHash", "password", "credentials");
    }
}
