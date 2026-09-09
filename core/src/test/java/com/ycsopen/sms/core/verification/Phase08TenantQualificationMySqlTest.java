package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.tenant.ContactVerificationService;
import com.ycsopen.sms.core.service.tenant.TenantEligibilityPolicy;
import com.ycsopen.sms.core.service.tenant.TenantMaintenanceService;
import com.ycsopen.sms.core.service.tenant.TenantRegistrationService;
import com.ycsopen.sms.core.service.tenant.TenantReviewService;
import com.ycsopen.sms.core.service.tenant.TenantService;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Physical Phase 08 persistence, transition, concurrency, and immutable-event proof. */
@SpringBootTest(classes = Phase08TenantQualificationMySqlTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Phase08TenantQualificationMySqlTest {
    private static Phase03ServiceHarness.ServiceSession mysql;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        mysql = Phase03ServiceHarness.startMySql();
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + mysql.host() + ":" + mysql.port()
                + "/phase01?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");
        registry.add("spring.datasource.username", mysql::username);
        registry.add("spring.datasource.password", mysql::password);
    }

    @AfterAll
    static void stopMySql() {
        if (mysql != null) mysql.close();
    }

    @Autowired TenantRepository tenants;
    @Autowired TenantAccountRepository accounts;
    @Autowired UserRepository users;
    @Autowired TenantReviewService reviews;
    @Autowired TenantMaintenanceService maintenance;
    @Autowired TenantRegistrationService registrations;
    @Autowired TenantEligibilityPolicy eligibility;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void realRepositoriesEnforceApprovalMaintenanceStatusRecertificationAndImmutableHistory() {
        Tenant tenant = pendingTenant();
        tenant = tenants.saveAndFlush(tenant);
        User administrator = disabledAdministrator(tenant.getId());
        administrator = users.saveAndFlush(administrator);
        tenant.setInitialAdminUserId(administrator.getId());
        tenant = tenants.saveAndFlush(tenant);
        long tenantId = tenant.getId();
        long administratorId = administrator.getId();
        long pendingRevision = tenant.getQualificationRevision();

        TenantReviewService.ReviewView approved = reviews.decide(tenantId, pendingRevision,
                TenantReviewService.Decision.APPROVE, "人工核验通过", true, "101");
        assertThat(approved.verificationStatus()).isEqualTo(Tenant.VerificationStatus.VERIFIED);
        assertThat(approved.lifecycleStatus()).isEqualTo(Tenant.LifecycleStatus.TRIAL);
        assertThat(users.findById(administratorId).orElseThrow().getStatus())
                .isEqualTo(User.UserStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_accounts WHERE tenant_id = ?",
                Integer.class, tenantId)).isOne();
        assertThatThrownBy(() -> reviews.decide(approved.tenantId(), approved.qualificationRevision(),
                TenantReviewService.Decision.APPROVE, "重复审核", true, "101"))
                .isInstanceOf(TenantReviewService.ReviewFailure.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_accounts WHERE tenant_id = ?",
                Integer.class, tenantId)).isOne();

        TenantMaintenanceService.ProfileResult profile = maintenance.updateProfile(tenantId,
                approved.qualificationRevision(), new TenantMaintenanceService.ProfileUpdate(
                        "新简称", "新联系人", "上海市新经营地址", 3, "商务经理", "互联网"),
                "客户确认基础资料更新", "102");
        assertThat(profile.verificationStatus()).isEqualTo(Tenant.VerificationStatus.VERIFIED);
        assertThat(jdbc.queryForObject("SELECT customer_level FROM tenants WHERE id = ?",
                Integer.class, tenantId)).isEqualTo(3);
        assertThatThrownBy(() -> maintenance.updateProfile(tenantId, approved.qualificationRevision(),
                new TenantMaintenanceService.ProfileUpdate("过期更新", null, null, null, null, null),
                "并发旧版本", "102")).hasMessage("QUALIFICATION_REVISION_STALE");

        TenantAccount initialAccount = accounts.findByTenantId(tenantId).orElseThrow();
        int initialAccountRevision = initialAccount.getVersion();
        TenantMaintenanceService.AccountStatusResult disabled = maintenance.changeAccountStatus(
                tenantId, initialAccountRevision, TenantAccount.Status.DISABLED, "运营停用", "103");
        assertThatThrownBy(() -> maintenance.changeAccountStatus(tenantId, initialAccountRevision,
                TenantAccount.Status.ARREARS_FROZEN, "并发旧版本", "103"))
                .hasMessage("ACCOUNT_REVISION_STALE");
        assertThatThrownBy(() -> eligibility.requireNewWorkAllowed(tenantId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("TENANT_ACCOUNT_INELIGIBLE");
        assertThat(maintenance.history(tenantId))
                .extracting(TenantMaintenanceService.QualificationEventView::action)
                .contains("APPROVED", "PROFILE_UPDATED", "ACCOUNT_STATUS_UPDATED");
        TenantMaintenanceService.AccountStatusResult normal = maintenance.changeAccountStatus(
                tenantId, disabled.revision(), TenantAccount.Status.NORMAL, "运营恢复", "103");
        assertThat(normal.revision()).isGreaterThan(disabled.revision());
        assertThatCode(() -> eligibility.requireNewWorkAllowed(tenantId)).doesNotThrowAnyException();

        var recertified = registrations.submitOwn(Long.toString(administratorId),
                recertificationRequest(), "upload-session", "verified-challenge");
        assertThat(recertified.verificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
        assertThat(recertified.lifecycleStatus()).isEqualTo(Tenant.LifecycleStatus.TRIAL);
        Tenant persisted = tenants.findById(tenantId).orElseThrow();
        assertThat(persisted.getInspectionStatus()).isEqualTo(Tenant.InspectionStatus.NOT_STARTED);
        assertThatThrownBy(() -> eligibility.requireNewWorkAllowed(tenantId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("TENANT_QUALIFICATION_REQUIRED");

        assertThat(maintenance.history(tenantId))
                .extracting(TenantMaintenanceService.QualificationEventView::action)
                .containsExactly("APPROVED", "PROFILE_UPDATED", "ACCOUNT_STATUS_UPDATED",
                        "ACCOUNT_STATUS_UPDATED", "SUBMITTED");
        assertThat(jdbc.queryForList("SELECT changed_fields FROM tenant_qualification_events WHERE tenant_id = ?",
                        String.class, tenantId))
                .allMatch(fields -> !fields.contains("新简称") && !fields.contains("上海市新经营地址"));
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE tenant_qualification_events SET action='REWRITTEN' WHERE tenant_id=?", tenantId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM tenant_qualification_events WHERE tenant_id=?", tenantId))
                .isInstanceOf(DataAccessException.class);
        // Phase 09 owns the next additive migrations; this regression guard must
        // track the latest schema rather than freeze the Phase 08 version.
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1801");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void twoCoordinatedAccountStatusTransactionsProduceOneWinnerAndOneStaleLoser()
            throws Exception {
        Tenant tenant = verifiedTenant("T-PHASE08-RACE", "91350211M000100Y54");
        tenant = tenants.saveAndFlush(tenant);
        long tenantId = tenant.getId();
        TenantAccount account = new TenantAccount();
        account.setTenantId(tenantId);
        account.setStatus(TenantAccount.Status.NORMAL);
        account = accounts.saveAndFlush(account);
        int sharedRevision = account.getVersion();
        CyclicBarrier insideTransactions = new CyclicBarrier(2);

        List<RaceOutcome> outcomes;
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            var disable = workers.submit(() -> competeForStatus(tenantId, sharedRevision,
                    TenantAccount.Status.DISABLED, "并发停用", insideTransactions));
            var freeze = workers.submit(() -> competeForStatus(tenantId, sharedRevision,
                    TenantAccount.Status.ARREARS_FROZEN, "并发欠费冻结", insideTransactions));
            outcomes = List.of(disable.get(20, TimeUnit.SECONDS),
                    freeze.get(20, TimeUnit.SECONDS));
        }

        assertThat(outcomes).extracting(RaceOutcome::connectionId).doesNotHaveDuplicates();
        assertThat(outcomes).filteredOn(RaceOutcome::winner).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.winner())
                .extracting(RaceOutcome::failure)
                .containsExactly("ACCOUNT_REVISION_STALE");
        TenantAccount persisted = accounts.findByTenantId(tenantId).orElseThrow();
        TenantAccount.Status winningStatus = outcomes.stream().filter(RaceOutcome::winner)
                .findFirst().orElseThrow().status();
        assertThat(persisted.getStatus()).isEqualTo(winningStatus);
        assertThat(persisted.getVersion()).isEqualTo(sharedRevision + 1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenant_qualification_events
                 WHERE tenant_id = ? AND action = 'ACCOUNT_STATUS_UPDATED'
                """, Integer.class, tenantId)).isOne();
    }

    private RaceOutcome competeForStatus(long tenantId, int expectedRevision,
                                         TenantAccount.Status target, String reason,
                                         CyclicBarrier insideTransactions) {
        AtomicLong connectionId = new AtomicLong();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            return transaction.execute(status -> {
                connectionId.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                await(insideTransactions);
                TenantMaintenanceService.AccountStatusResult result = maintenance.changeAccountStatus(
                        tenantId, expectedRevision, target, reason, "104");
                return new RaceOutcome(true, result.status(), null, connectionId.get());
            });
        } catch (TenantMaintenanceService.MaintenanceFailure failure) {
            return new RaceOutcome(false, null, failure.getMessage(), connectionId.get());
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to coordinate open transactions", failure);
        }
    }

    private static Tenant pendingTenant() {
        Tenant tenant = new Tenant();
        tenant.setTenantNo("T-PHASE08");
        tenant.setShortName("示例机构");
        tenant.setFullName("示例机构有限公司");
        tenant.setUnifiedSocialCreditCode("91350211M000100Y46");
        tenant.setLegalRepName("张三");
        tenant.setContactName("李四");
        tenant.setRegisteredCapital("100万元人民币");
        tenant.setBusinessScope("软件开发");
        tenant.setRegisteredAddress("上海市注册地址1号");
        tenant.setBusinessAddress("上海市经营地址2号");
        tenant.setLicenseValidUntil(java.time.LocalDate.of(2099, 12, 31));
        tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.SUBMITTED);
        tenant.setInspectionStatus(Tenant.InspectionStatus.COMPLETED);
        tenant.setInspectionCompanyName("示例机构有限公司");
        tenant.setInspectionCreditCode("91350211M000100Y46");
        tenant.setInspectionConfidence(0.98);
        return tenant;
    }

    private static Tenant verifiedTenant(String tenantNo, String creditCode) {
        Tenant tenant = new Tenant();
        tenant.setTenantNo(tenantNo);
        tenant.setShortName("竞态机构");
        tenant.setFullName("竞态机构有限公司");
        tenant.setUnifiedSocialCreditCode(creditCode);
        tenant.setVerificationStatus(Tenant.VerificationStatus.VERIFIED);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.TRIAL);
        tenant.setInspectionStatus(Tenant.InspectionStatus.COMPLETED);
        return tenant;
    }

    private static User disabledAdministrator(Long tenantId) {
        User user = new User();
        user.setUsername("phase08-admin");
        user.setPasswordHash("$2a$04$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuu");
        user.setEmail("owner@example.com");
        user.setUserType(User.UserType.TENANT_ADMIN);
        user.setTenantId(tenantId);
        user.setStatus(User.UserStatus.DISABLED);
        return user;
    }

    private static TenantRegistrationRequest recertificationRequest() {
        return new TenantRegistrationRequest("新简称", "示例机构有限公司", "91350211M000100Y46",
                "12345678-1234-1234-1234-123456789abc", "pobj_v1_license", "张三",
                "11010519491231002X", "pobj_v1_front", "pobj_v1_back", "新联系人",
                "11010519491231002X", "13800138000", null, null, false,
                "100万元人民币", "软件开发", "上海市注册地址1号", "上海市新经营地址",
                java.time.LocalDate.of(2099, 12, 31));
    }

    private record RaceOutcome(boolean winner, TenantAccount.Status status, String failure,
                               long connectionId) { }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    @EnableJpaRepositories(basePackageClasses = TenantRepository.class)
    @EntityScan(basePackageClasses = Tenant.class)
    @Import({TenantReviewService.class, TenantMaintenanceService.class, TenantEligibilityPolicy.class,
            TenantRegistrationService.class, TenantService.class, Dependencies.class})
    static class Application { }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean TenantRegistrationProtectionAdapter protection() {
            return mock(TenantRegistrationProtectionAdapter.class);
        }
        @Bean ContactVerificationService contacts() { return mock(ContactVerificationService.class); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
    }
}
