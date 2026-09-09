package com.ycsopen.sms.core.service.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrialPrepaidLedgerServiceTest {
    private JdbcTemplate jdbc;
    private TrialPrepaidLedgerService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:trial-prepaid-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE trial_accounts(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL UNIQUE, status VARCHAR(32) NOT NULL, quota_total INT NOT NULL, quota_remaining INT NOT NULL, start_at TIMESTAMP NOT NULL, end_at TIMESTAMP NOT NULL, version INT NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE trial_consumption_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, message_ref VARCHAR(64) NOT NULL, business_type VARCHAR(64) NOT NULL, quota_delta INT NOT NULL, amount_mil BIGINT NOT NULL, entry_type VARCHAR(32) NOT NULL, state VARCHAR(32) NOT NULL, actor VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, message_ref))");
        jdbc.execute("CREATE TABLE prepaid_accounts(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL UNIQUE, balance_mil BIGINT NOT NULL, frozen_mil BIGINT NOT NULL, status VARCHAR(32) NOT NULL, version INT NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE prepaid_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, business_doc_id VARCHAR(64) NOT NULL UNIQUE, business_type VARCHAR(64) NOT NULL, channel_code VARCHAR(64) NULL, price_mil BIGINT NOT NULL, quantity INT NOT NULL, amount_mil BIGINT NOT NULL, state VARCHAR(32) NOT NULL, transaction_ref VARCHAR(128) NULL, actor VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE balance_audit_entries(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, business_doc_id VARCHAR(64) NOT NULL, mutation_type VARCHAR(32) NOT NULL, amount_mil BIGINT NOT NULL, before_balance_mil BIGINT NOT NULL, after_balance_mil BIGINT NOT NULL, before_frozen_mil BIGINT NOT NULL, after_frozen_mil BIGINT NOT NULL, account_version INT NOT NULL, actor VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE trial_conversion_requests(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, trial_status VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, actor VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        service = new TrialPrepaidLedgerService(jdbc);
    }

    @Test
    void activationUsesPositiveQuotaAndDefaultValidityThenConsumptionFreezesExactlyOnce() {
        var overview = service.activateTrial(7, null, LocalDateTime.of(2026, 9, 9, 0, 0), null, "operator");
        assertThat(overview.quotaTotal()).isEqualTo(500);
        assertThat(overview.validUntil()).isEqualTo(LocalDateTime.of(2026, 9, 23, 0, 0));

        service.activateTrial(8, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(1), "operator");
        var frozen = service.consumeTrial(8, "MSG-1", "SMS", "tenant");
        assertThat(frozen.trialStatus()).isEqualTo("TRIAL_FROZEN");
        service.consumeTrial(8, "MSG-1", "SMS", "tenant");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trial_consumption_ledger WHERE tenant_id=8 AND entry_type='TRIAL_CONSUME'", Integer.class)).isEqualTo(1);
    }

    @Test
    void conversionIsAvailableFromDeclaredTrialStates() {
        service.activateTrial(7, 10, LocalDateTime.now(), LocalDateTime.now().plusDays(1), "operator");

        var request = service.requestConversion(7, "tenant");

        assertThat(request.status()).isEqualTo("REQUESTED");
        assertThat(request.trialStatus()).isEqualTo("TRIAL");
    }

    @Test
    void prepaidReserveConfirmReverseAreAtomicAndIdempotent() {
        service.creditForTest(7, 1_000);
        var reserved = service.reservePrepaid(7, "DOC-1", "SMS", "CH_MAIN", 100, 2, "system");
        service.reservePrepaid(7, "DOC-1", "SMS", "CH_MAIN", 100, 2, "system");
        assertThat(reserved.amountMil()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT frozen_mil FROM prepaid_accounts WHERE tenant_id=7", Long.class)).isEqualTo(200);

        var confirmed = service.confirmPrepaid("DOC-1", "TX-1", "receipt");
        service.confirmPrepaid("DOC-1", "TX-1", "receipt");
        assertThat(confirmed.state()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT balance_mil FROM prepaid_accounts WHERE tenant_id=7", Long.class)).isEqualTo(800);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prepaid_ledger WHERE business_doc_id='DOC-1'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM balance_audit_entries WHERE business_doc_id='DOC-1' AND mutation_type='CONFIRM'", Integer.class)).isEqualTo(1);
    }

    @Test
    void insufficientBalanceRejectsWithoutLedgerAndAuditIsAppendOnly() {
        service.creditForTest(7, 100);

        assertThatThrownBy(() -> service.reservePrepaid(7, "DOC-2", "SMS", "CH_MAIN", 200, 1, "system"))
                .hasMessageContaining("可用余额不足");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prepaid_ledger", Integer.class)).isZero();
        service.reservePrepaid(7, "DOC-3", "SMS", "CH_MAIN", 50, 1, "system");
        service.reversePrepaid("DOC-3", "receipt");
        service.reversePrepaid("DOC-3", "receipt");
        assertThat(service.audits(7L)).extracting("mutationType").containsExactly("REVERSE", "RESERVE");
    }

    @Test
    void expiredTrialFreezesWithoutWritingConsumptionAndUnknownPrepaidDocIsBusinessFailure() {
        service.activateTrial(7, 2, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), "operator");

        assertThatThrownBy(() -> service.consumeTrial(7, "MSG-EXPIRED", "SMS", "tenant"))
                .hasMessageContaining("试用有效期已过");

        assertThat(service.overview(7).trialStatus()).isEqualTo("TRIAL_FROZEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trial_consumption_ledger WHERE tenant_id=7 AND entry_type='TRIAL_CONSUME'", Integer.class)).isZero();
        assertThatThrownBy(() -> service.confirmPrepaid("MISSING-DOC", "TX-1", "receipt"))
                .hasMessageContaining("预付费业务单不存在");
    }

    @Test
    void reserveUsesAccountVersionGuardAndDoesNotOverFreeze() {
        service.creditForTest(7, 300);

        service.reservePrepaid(7, "DOC-4", "SMS", "CH_MAIN", 200, 1, "system");

        assertThatThrownBy(() -> service.reservePrepaid(7, "DOC-5", "SMS", "CH_MAIN", 200, 1, "system"))
                .hasMessageContaining("可用余额不足");
        assertThat(jdbc.queryForObject("SELECT frozen_mil FROM prepaid_accounts WHERE tenant_id=7", Long.class)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prepaid_ledger", Integer.class)).isEqualTo(1);
    }

    @Test
    void consumptionAndBalanceQueriesFilterWithoutMutatingFinancialHistory() {
        service.activateTrial(7, 2, LocalDateTime.now(), LocalDateTime.now().plusDays(1), "operator");
        service.consumeTrial(7, "MSG-1", "SMS", "tenant");

        assertThat(service.consumption(7L, "SMS")).hasSize(1);
        assertThat(service.consumption(7L, "VOICE")).isEmpty();
    }
}
