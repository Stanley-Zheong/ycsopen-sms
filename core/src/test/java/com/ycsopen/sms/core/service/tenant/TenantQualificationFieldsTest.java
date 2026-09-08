package com.ycsopen.sms.core.service.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes JSON -> validation -> both submission services -> real JPA column round trips. */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({TenantService.class, TenantRegistrationService.class, TenantQualificationFieldsTest.Configuration.class})
class TenantQualificationFieldsTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @Autowired TenantRegistrationService registrations;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @MockBean ContactVerificationService contacts;
    @MockBean TenantRegistrationProtectionAdapter protection;

    @BeforeEach void createEventStore() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_qualification_events (tenant_id BIGINT, action VARCHAR(64), before_verification_status VARCHAR(32), after_verification_status VARCHAR(32), before_lifecycle_status VARCHAR(32), after_lifecycle_status VARCHAR(32), changed_fields VARCHAR(1000), actor VARCHAR(64))");
    }

    @Test void publicRegistrationRoundTripsAllFiveBusinessFields() throws Exception {
        ObjectNode input = completeInput();
        var submitted = register(input);
        entities.flush();
        entities.clear();
        Tenant stored = tenants.findById(submitted.tenantId()).orElseThrow();
        assertBusinessFields(stored, "100万元人民币", "软件开发与信息技术服务", "上海市注册地址1号", "上海市经营地址2号", LocalDate.of(2099, 12, 31));
        assertThat(stored.getVerificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
    }

    @Test void authenticatedRecertificationReplacesAllFiveBusinessFieldsWithoutCreatingAnotherAdmin() throws Exception {
        var submitted = register(completeInput());
        Tenant existing = tenants.findById(submitted.tenantId()).orElseThrow();
        existing.setVerificationStatus(Tenant.VerificationStatus.REJECTED);
        User admin = users.findById(existing.getInitialAdminUserId()).orElseThrow();
        admin.setStatus(User.UserStatus.ACTIVE);
        tenants.saveAndFlush(existing);
        users.saveAndFlush(admin);
        entities.clear();
        ObjectNode replacement = completeInput();
        replacement.put("registeredCapital", "200万元人民币");
        replacement.put("businessScope", "软件开发、技术咨询");
        replacement.put("registeredAddress", "北京市注册地址3号");
        replacement.put("businessAddress", "北京市经营地址4号");
        replacement.put("licenseValidUntil", "2098-06-30");
        registrations.submitOwn(admin.getId().toString(), decode(replacement), "upload", "new-challenge");
        entities.flush();
        entities.clear();
        Tenant stored = tenants.findById(existing.getId()).orElseThrow();
        assertBusinessFields(stored, "200万元人民币", "软件开发、技术咨询", "北京市注册地址3号", "北京市经营地址4号", LocalDate.of(2098, 6, 30));
        assertThat(users.count()).isEqualTo(1);
        assertThat(stored.getVerificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
    }

    @ParameterizedTest(name = "case {index}: rejects invalid {0} before consumption")
    @MethodSource("invalidFields")
    void missingOrInvalidBusinessFieldsCannotConsumeReceiptsOrEvidence(String field, String value) throws Exception {
        User actor = new User();
        actor.setUsername("existing_admin"); actor.setPasswordHash("existing-hash");
        actor.setTenantId(42L); actor.setUserType(User.UserType.TENANT_ADMIN);
        users.saveAndFlush(actor);
        ObjectNode input = completeInput();
        if (value == null) input.remove(field); else input.put(field, value);
        TenantRegistrationRequest request = decode(input);
        assertThatThrownBy(() -> registrations.register(request, "upload", "challenge", "initial_admin", "GoodPassword9!", "owner@example.com"))
                .isInstanceOf(TenantQualificationValidator.ValidationFailure.class);
        assertThatThrownBy(() -> registrations.submitOwn(actor.getId().toString(), request, "upload", "challenge"))
                .isInstanceOf(TenantQualificationValidator.ValidationFailure.class);
        verifyNoInteractions(contacts, protection);
        assertThat(tenants.count()).isZero();
    }

    @Test void rejectsMalformedCalendarDateBeforeAnySubmissionSideEffect() throws Exception {
        ObjectNode input = completeInput(); input.put("licenseValidUntil", "2099-02-30");
        assertThatThrownBy(() -> decode(input)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        verifyNoInteractions(contacts, protection);
    }

    @Test void acceptsTodayAndMaximumTextBoundsAndPreservesFieldsWhenTrademarkUseChanges() throws Exception {
        ObjectNode input = completeInput();
        input.put("registeredCapital", "资".repeat(50));
        input.put("businessScope", "营".repeat(10_000));
        input.put("registeredAddress", "址".repeat(255));
        input.put("businessAddress", "址".repeat(255));
        input.put("licenseValidUntil", LocalDate.now().toString());
        TenantRegistrationRequest request = decode(input).withTrademarkUse(false);
        var submitted = registrations.register(request, "upload", "challenge", "initial_admin", "GoodPassword9!", "owner@example.com");
        entities.flush(); entities.clear();
        assertBusinessFields(tenants.findById(submitted.tenantId()).orElseThrow(), "资".repeat(50), "营".repeat(10_000), "址".repeat(255), "址".repeat(255), LocalDate.now());
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.concat(
                Stream.of("registeredCapital", "businessScope", "registeredAddress", "businessAddress", "licenseValidUntil").map(field -> Arguments.of(field, null)),
                Stream.of(Arguments.of("registeredCapital", " "), Arguments.of("businessScope", " "),
                        Arguments.of("registeredAddress", " "), Arguments.of("businessAddress", " "),
                        Arguments.of("registeredCapital", "x".repeat(51)), Arguments.of("businessScope", "x".repeat(10_001)),
                        Arguments.of("registeredAddress", "x".repeat(256)), Arguments.of("businessAddress", "x".repeat(256)),
                        Arguments.of("licenseValidUntil", LocalDate.now().minusDays(1).toString())));
    }

    private com.ycsopen.sms.core.web.dto.TenantRegistrationResponse register(ObjectNode input) throws Exception {
        return registrations.register(decode(input), "upload", "challenge", "initial_admin", "GoodPassword9!", "owner@example.com");
    }
    private static TenantRegistrationRequest decode(ObjectNode input) throws Exception { return JSON.treeToValue(input, TenantRegistrationRequest.class); }
    private static ObjectNode completeInput() throws Exception {
        return (ObjectNode) JSON.readTree("""
                {"shortName":"示例机构","fullName":"示例机构有限公司","unifiedSocialCreditCode":"91350211M000100Y46",
                 "registrationObjectSessionId":"12345678-1234-1234-1234-123456789abc","businessLicenseObjectId":"pobj_v1_license",
                 "legalRepName":"张三","legalRepIdNo":"11010519491231002X","legalRepIdFrontObjectId":"pobj_v1_front","legalRepIdBackObjectId":"pobj_v1_back",
                 "contactName":"李四","contactIdNo":"11010519491231002X","contactPhone":"13800138000",
                 "registeredCapital":"100万元人民币","businessScope":"软件开发与信息技术服务",
                 "registeredAddress":"上海市注册地址1号","businessAddress":"上海市经营地址2号","licenseValidUntil":"2099-12-31"}
                """);
    }
    private static void assertBusinessFields(Tenant tenant, String capital, String scope, String registered, String business, LocalDate expiry) {
        assertThat(tenant.getRegisteredCapital()).isEqualTo(capital);
        assertThat(tenant.getBusinessScope()).isEqualTo(scope);
        assertThat(tenant.getRegisteredAddress()).isEqualTo(registered);
        assertThat(tenant.getBusinessAddress()).isEqualTo(business);
        assertThat(tenant.getLicenseValidUntil()).isEqualTo(expiry);
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
    }
}
