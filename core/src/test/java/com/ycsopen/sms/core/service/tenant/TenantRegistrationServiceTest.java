package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.*;
import com.ycsopen.sms.core.repository.*;
import com.ycsopen.sms.core.web.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantRegistrationServiceTest {
    TenantRepository tenants = mock(TenantRepository.class);
    UserRepository users = mock(UserRepository.class);
    ContactVerificationService contacts = mock(ContactVerificationService.class);
    TenantRegistrationProtectionAdapter protection = mock(TenantRegistrationProtectionAdapter.class);
    TenantRegistrationService service;

    @BeforeEach void setup() {
        when(tenants.saveAndFlush(any())).thenAnswer(invocation -> { Tenant tenant = invocation.getArgument(0); if (tenant.getId() == null) tenant.setId(42L); return tenant; });
        when(users.saveAndFlush(any())).thenAnswer(invocation -> { User user = invocation.getArgument(0); user.setId(81L); return user; });
        service = new TenantRegistrationService(tenants, users, new TenantService(tenants, mock(TenantAccountRepository.class), protection), protection, contacts, new BCryptPasswordEncoder());
    }

    @Test void completeRegistrationCreatesExactlyOneDisabledAdminWithBcryptAndSafePendingResponse() throws Exception {
        var result = service.register(request(), "upload", "challenge", "initial_admin", "GoodPassword9!", "owner@example.com");
        assertThat(result.verificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
        assertThat(result.submittedAt()).isNotNull();
        assertThat(result.verifiedAt()).isNull();
        ArgumentCaptor<User> account = ArgumentCaptor.forClass(User.class);
        verify(users, times(1)).saveAndFlush(account.capture());
        assertThat(account.getValue().getStatus()).isEqualTo(User.UserStatus.DISABLED);
        assertThat(account.getValue().getUserType()).isEqualTo(User.UserType.TENANT_ADMIN);
        assertThat(account.getValue().getTenantId()).isEqualTo(42L);
        assertThat(new BCryptPasswordEncoder().matches("GoodPassword9!", account.getValue().getPasswordHash())).isTrue();
        verify(contacts).consumeVerified("challenge", "13800138000");
        verify(tenants).appendSubmissionEvent(42L, "UNVERIFIED", "SUBMITTED", "public-registration");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        assertThat(json).doesNotContain("13800138000", "11010519491231002X", "pobj_", "upload", "password", "challenge");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("reason")).isNotNull().isEqualTo(com.fasterxml.jackson.databind.node.NullNode.instance);
    }

    @ParameterizedTest
    @EnumSource(value = Tenant.VerificationStatus.class, names = {"REJECTED", "SUPPLEMENT_REQUIRED"})
    void ownStatusReturnsThePersistedSafeReasonWithoutExpandingProtectedData(Tenant.VerificationStatus state) throws Exception {
        User actor = new User(); actor.setId(81L); actor.setTenantId(42L); actor.setUserType(User.UserType.TENANT_ADMIN);
        when(users.findById(81L)).thenReturn(Optional.of(actor));
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setTenantNo("T42"); tenant.setVerificationStatus(state);
        tenant.setQualificationReason("营业执照照片不清晰，请重新上传。");
        tenant.setLegalRepName("张三"); tenant.setContactName("李四");
        org.springframework.test.util.ReflectionTestUtils.setField(tenant, "businessLicenseObjectId", "pobj_v1_private_evidence");
        org.springframework.test.util.ReflectionTestUtils.setField(tenant, "contactPhoneEncrypted", new byte[]{1,2,3});
        when(tenants.findById(42L)).thenReturn(Optional.of(tenant));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        String serialized = mapper.writeValueAsString(service.statusOwn("81"));
        var json = mapper.readTree(serialized);
        assertThat(json.path("reason").asText()).isEqualTo("营业执照照片不清晰，请重新上传。");
        java.util.Set<String> fields = new java.util.HashSet<>(); json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).isSubsetOf("tenantId", "tenantNo", "shortName", "fullName", "verificationStatus",
                "lifecycleStatus", "submittedAt", "verifiedAt", "verificationUpdatedAt", "revision", "reason",
                "unifiedSocialCreditCode", "legalRepresentativeName", "contactName", "registeredCapital", "businessScope",
                "registeredAddress", "businessAddress", "licenseValidUntil", "trademarkUse", "businessLicensePresent",
                "legalRepresentativeIdentityPresent", "legalRepresentativeIdFrontPresent", "legalRepresentativeIdBackPresent",
                "contactIdentityPresent", "contactPhonePresent", "shortlinkProofPresent", "trademarkProofPresent");
        assertThat(serialized).doesNotContain("pobj_", "token", "locator", "codeHash", "password", "credential", "contactPhoneEncrypted");
    }

    @Test void mapsTheDatabaseUniquenessRaceToAStableConflict() {
        doThrow(new DataIntegrityViolationException("private SQL and input")).when(tenants).saveAndFlush(any());
        assertThatThrownBy(() -> service.register(request(), "upload", "challenge", "initial_admin", "GoodPassword9!", "owner@example.com"))
                .isInstanceOf(TenantRegistrationService.SubmissionFailure.class).hasMessage("DUPLICATE_REGISTRATION");
    }

    @Test void rejectsUnsafeCredentialsBeforeConsumingContactOrClaimingEvidence() {
        for (String password : new String[]{"short", "a".repeat(73), "onlylowercasepassword"}) {
            assertThatThrownBy(() -> service.register(request(), "upload", "challenge", "initial_admin", password, "owner@example.com"))
                    .hasMessage("INVALID_ADMIN_CREDENTIALS");
        }
        assertThatThrownBy(() -> service.register(request(), "upload", "challenge", "<bad>", "GoodPassword9!", "owner@example.com")).hasMessage("INVALID_ADMIN_CREDENTIALS");
        assertThatThrownBy(() -> service.register(request(), "upload", "challenge", "initial_admin", "GoodPassword9!", "invalid")).hasMessage("INVALID_ADMIN_CREDENTIALS");
        verifyNoInteractions(contacts, protection);
    }

    @Test void ownTenantComesFromTheActiveAccountAndCannotBeSelectedByRequest() throws Exception {
        User actor = new User(); actor.setId(81L); actor.setTenantId(42L); actor.setUserType(User.UserType.TENANT_ADMIN);
        when(users.findById(81L)).thenReturn(Optional.of(actor));
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setTenantNo("T42"); tenant.setVerificationStatus(Tenant.VerificationStatus.REJECTED);
        tenant.setQualificationReason("请补充清晰的营业执照。");
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.of(tenant));
        when(tenants.findById(42L)).thenReturn(Optional.of(tenant));
        var result = service.submitOwn("81", request(), "upload", "challenge");
        assertThat(result.tenantId()).isEqualTo(42L);
        assertThat(result.verificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertThat(mapper.valueToTree(result).get("reason")).isNotNull().isEqualTo(com.fasterxml.jackson.databind.node.NullNode.instance);
        assertThat(mapper.valueToTree(service.statusOwn("81")).get("reason")).isNotNull().isEqualTo(com.fasterxml.jackson.databind.node.NullNode.instance);
        verify(users, never()).saveAndFlush(any());
        actor.setStatus(User.UserStatus.DISABLED);
        assertThatThrownBy(() -> service.statusOwn("81")).isInstanceOf(AccessDeniedException.class);
    }

    @Test void pendingOwnSubmissionCannotBeOverwritten() {
        User actor = new User(); actor.setTenantId(42L); actor.setUserType(User.UserType.TENANT_ADMIN);
        when(users.findById(81L)).thenReturn(Optional.of(actor));
        Tenant tenant = new Tenant(); tenant.setId(42L); tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.of(tenant));
        assertThatThrownBy(() -> service.submitOwn("81", request(), "upload", "challenge")).hasMessage("QUALIFICATION_ALREADY_PENDING");
        verifyNoInteractions(contacts);
    }

    public static TenantRegistrationRequest request() {
        return new TenantRegistrationRequest("示例机构", "示例机构有限公司", "91350211M000100Y46", "12345678-1234-1234-1234-123456789abc", "pobj_v1_license", "张三", "11010519491231002X", "pobj_v1_front", "pobj_v1_back", "李四", "11010519491231002X", "13800138000", null, null,
                false, "100万元人民币", "软件开发", "上海市注册地址1号", "上海市经营地址2号", java.time.LocalDate.of(2099, 12, 31));
    }
}
