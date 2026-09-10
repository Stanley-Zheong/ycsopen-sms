package com.ycsopen.sms.core.service.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantCooperationTerminationServiceTest {
    private JdbcTemplate jdbc;
    private TenantCooperationTerminationService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase49-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new TenantCooperationTerminationService(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void prepaidOutstandingBlocksUntilClearedThenApprovalAndEffectRevokeEveryMutableResource() {
        seedTenant(42, "SIGNED", "PREPAID");
        jdbc.update("INSERT INTO prepaid_accounts(tenant_id,balance_mil,frozen_mil,status) VALUES (42,8000,1000,'ACTIVE')");
        seedMutableResources(42);

        var blocked = service.requestTermination(new TenantCooperationTerminationService.TerminationCommand(
                42, "VOLUNTARY", "termination-ticket:T-4901"), "ops");

        assertThat(blocked.request().requestStatus()).isEqualTo("BLOCKED_CLEARANCE");
        assertThat(blocked.request().clearanceSnapshotJson()).contains("PREPAID_REFUND").contains("\"clearancePassed\":false");
        assertThat(blocked.participants()).hasSize(15);
        assertThat(blocked.participants()).anySatisfy(participant -> {
            assertThat(participant.participantCode()).isEqualTo("API_KEYS");
            assertThat(participant.participantState()).isEqualTo("READY_TO_REVOKE");
            assertThat(participant.blockerCount()).isEqualTo(1);
        });

        jdbc.update("UPDATE prepaid_accounts SET balance_mil=0, frozen_mil=0 WHERE tenant_id=42");
        var cleared = service.refreshClearance(blocked.request().id(), "finance");
        var approved = service.approve(cleared.request().id(),
                new TenantCooperationTerminationService.ApprovalCommand("cleared"), "admin");
        var effective = service.effect(approved.request().id(), "admin");

        assertThat(effective.request().requestStatus()).isEqualTo("EFFECTIVE");
        assertThat(effective.request().compensationJson()).contains("apiKeysRevoked").contains("templatesDeactivated");
        assertThat(effective.participants()).anySatisfy(participant -> {
            assertThat(participant.participantCode()).isEqualTo("HTTP_ACCEPTANCE");
            assertThat(participant.participantState()).isEqualTo("REVOKED");
        });
        assertThat(effective.participants()).anySatisfy(participant -> {
            assertThat(participant.participantCode()).isEqualTo("IRREVERSIBILITY");
            assertThat(participant.participantState()).isEqualTo("RETAINED");
        });
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=42", String.class))
                .isEqualTo("TERMINATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_api_keys WHERE tenant_id=42 AND status='ACTIVE'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_protocol_credentials WHERE tenant_id=42 AND status='ACTIVE'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE tenant_id=42 AND status <> 'DISABLED'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE tenant_id=42 AND expires_at > CURRENT_TIMESTAMP", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bulk_sendings WHERE tenant_id=42 AND task_status <> 'CANCELLED'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_termination_audits WHERE request_id=?", Integer.class,
                effective.request().id())).isEqualTo(4);
        assertThat(service.detail(effective.request().id()).audits()).extracting("action")
                .containsExactly("REQUEST", "REFRESH_CLEARANCE", "APPROVE", "EFFECT");
    }

    @Test
    void postpaidUnsettledEvidenceBlocksApprovalWithExactClearanceReason() {
        seedTenant(43, "FROZEN", "POSTPAID");
        jdbc.update("INSERT INTO statements(tenant_id, settlement_status) VALUES (43,'PENDING_SETTLEMENT')");
        var blocked = service.requestTermination(new TenantCooperationTerminationService.TerminationCommand(
                43, "LONG_TERM_ARREARS", "statement:2026-08"), "finance");

        assertThat(blocked.request().requestStatus()).isEqualTo("BLOCKED_CLEARANCE");
        assertThat(blocked.request().clearanceSnapshotJson()).contains("POSTPAID_SETTLEMENT").contains("后付费账单或结算记录未完成");
        assertThatThrownBy(() -> service.approve(blocked.request().id(),
                new TenantCooperationTerminationService.ApprovalCommand("not cleared"), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("终止未通过清算检查");
    }

    @Test
    void terminatedTenantIsIrreversibleAndIngressFenceRejectsNewWork() {
        seedTenant(44, "SIGNED", "PREPAID");
        var detail = service.requestTermination(new TenantCooperationTerminationService.TerminationCommand(
                44, "SEVERE_VIOLATION", "violation-case:V-44"), "ops");
        service.approve(detail.request().id(), new TenantCooperationTerminationService.ApprovalCommand("confirmed"), "admin");
        service.effect(detail.request().id(), "admin");

        assertThatThrownBy(() -> service.effect(detail.request().id(), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须先审批通过");
        assertThatThrownBy(() -> service.requireTenantNotTerminated(44, "HTTP"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 已拒绝终止机构");
        assertThat(service.list(44L, "EFFECTIVE")).hasSize(1);
    }

    @Test
    void staleFinanceClearanceCannotBecomeEffectiveAfterApproval() {
        seedTenant(45, "SIGNED", "POSTPAID");
        jdbc.update("INSERT INTO tenant_api_keys(tenant_id,status) VALUES (45,'ACTIVE')");
        var detail = service.requestTermination(new TenantCooperationTerminationService.TerminationCommand(
                45, "VOLUNTARY", "termination-ticket:T-45"), "ops");
        var approved = service.approve(detail.request().id(),
                new TenantCooperationTerminationService.ApprovalCommand("cleared"), "admin");
        jdbc.update("INSERT INTO statements(tenant_id, settlement_status) VALUES (45,'PENDING_SETTLEMENT')");

        assertThatThrownBy(() -> service.effect(approved.request().id(), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("清算状态已失效");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_api_keys WHERE tenant_id=45 AND status='ACTIVE'", Integer.class))
                .isOne();
    }

    private void seedTenant(long tenantId, String lifecycle, String billingMode) {
        jdbc.update("INSERT INTO tenants(id,lifecycle_status,billing_mode) VALUES (?,?,?)", tenantId, lifecycle, billingMode);
        jdbc.update("INSERT INTO tenant_accounts(tenant_id,balance,frozen_amount,status) VALUES (?,?,?,'NORMAL')",
                tenantId, 0, 0);
    }

    private void seedMutableResources(long tenantId) {
        jdbc.update("INSERT INTO users(id,tenant_id,status) VALUES (1,?,'ACTIVE')", tenantId);
        jdbc.update("INSERT INTO user_sessions(id,user_id,tenant_id,expires_at) VALUES ('s1',1,?,?)",
                tenantId, Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
        jdbc.update("INSERT INTO tenant_api_keys(tenant_id,status) VALUES (?,'ACTIVE')", tenantId);
        jdbc.update("INSERT INTO tenant_protocol_credentials(tenant_id,status) VALUES (?,'ACTIVE')", tenantId);
        jdbc.update("INSERT INTO tenant_callback_configs(tenant_id,config_status) VALUES (?,'ACTIVE')", tenantId);
        jdbc.update("INSERT INTO signatures(tenant_id,audit_status) VALUES (?,'APPROVED')", tenantId);
        jdbc.update("INSERT INTO templates(tenant_id,audit_status) VALUES (?,'APPROVED')", tenantId);
        jdbc.update("INSERT INTO bulk_sendings(id,tenant_id,task_status) VALUES (100,?,'RUNNING')", tenantId);
        jdbc.update("INSERT INTO bulk_sending_items(bulk_id,send_status) VALUES (100,'PENDING')");
        jdbc.update("INSERT INTO uplink_records(tenant_id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO unsubscribe_records(tenant_id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO archive_manifests(tenant_id) VALUES (?)", tenantId);
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE tenants(id BIGINT PRIMARY KEY, lifecycle_status VARCHAR(32), billing_mode VARCHAR(32), termination_requested_by VARCHAR(64), termination_reason VARCHAR(32), termination_approved_by VARCHAR(64), termination_approved_at TIMESTAMP, termination_effective_date DATE, termination_settlement_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE tenant_accounts(tenant_id BIGINT, balance BIGINT, frozen_amount BIGINT, status VARCHAR(32))");
        jdbc.execute("CREATE TABLE prepaid_accounts(tenant_id BIGINT, balance_mil BIGINT, frozen_mil BIGINT, status VARCHAR(32))");
        jdbc.execute("CREATE TABLE statements(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, settlement_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE settlement_records(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, status VARCHAR(32))");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, tenant_id BIGINT, status VARCHAR(32))");
        jdbc.execute("CREATE TABLE user_sessions(id VARCHAR(128) PRIMARY KEY, user_id BIGINT, tenant_id BIGINT, expires_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_api_keys(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, status VARCHAR(32), revoked_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_protocol_credentials(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, status VARCHAR(32), revoked_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_callback_configs(tenant_id BIGINT PRIMARY KEY, config_status VARCHAR(32), paused_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE signatures(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, audit_status VARCHAR(32), audit_comment VARCHAR(500))");
        jdbc.execute("CREATE TABLE templates(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, audit_status VARCHAR(32), audit_comment VARCHAR(500))");
        jdbc.execute("CREATE TABLE bulk_sendings(id BIGINT PRIMARY KEY, tenant_id BIGINT, task_status VARCHAR(32), control_reason VARCHAR(500), end_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE bulk_sending_items(id BIGINT AUTO_INCREMENT PRIMARY KEY, bulk_id BIGINT, send_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE uplink_records(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT)");
        jdbc.execute("CREATE TABLE unsubscribe_records(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT)");
        jdbc.execute("CREATE TABLE archive_manifests(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT)");
        jdbc.execute("CREATE TABLE tenant_termination_requests(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, reason VARCHAR(32), request_evidence VARCHAR(1000), request_status VARCHAR(32), requested_by VARCHAR(64), requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, approved_by VARCHAR(64), approved_at TIMESTAMP, admin_opinion VARCHAR(500), effective_at TIMESTAMP, clearance_snapshot_json CLOB, participant_snapshot_json CLOB, compensation_json CLOB)");
        jdbc.execute("CREATE TABLE tenant_termination_participants(id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT, tenant_id BIGINT, participant_code VARCHAR(64), participant_name VARCHAR(100), participant_state VARCHAR(32), blocker_count BIGINT, evidence_json VARCHAR(4000), updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_termination_audits(id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT, tenant_id BIGINT, action VARCHAR(32), actor VARCHAR(64), result_status VARCHAR(32), evidence_json VARCHAR(4000), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }
}
