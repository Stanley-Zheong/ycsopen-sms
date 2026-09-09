package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.PolicyImportRequest;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.PolicyRule;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.RetryPolicy;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.SimulationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingCircuitPolicyServiceTest {
    private JdbcTemplate jdbc;
    private RoutingCircuitPolicyService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:routing-policy-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE routing_policy_versions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version_no VARCHAR(32) NOT NULL UNIQUE,
                    status VARCHAR(16) NOT NULL,
                    source_name VARCHAR(64) NOT NULL,
                    effective_at TIMESTAMP NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE routing_policy_rules (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version_id BIGINT NOT NULL,
                    priority INT NOT NULL,
                    condition_type VARCHAR(32) NOT NULL,
                    condition_value VARCHAR(128) NOT NULL,
                    target_type VARCHAR(32) NOT NULL,
                    target_ref VARCHAR(128) NOT NULL,
                    weight INT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE routing_circuit_states (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    channel_code VARCHAR(64) NOT NULL UNIQUE,
                    status VARCHAR(16) NOT NULL,
                    failure_count INT NOT NULL,
                    success_count INT NOT NULL,
                    latency_ms INT NOT NULL,
                    opened_at TIMESTAMP NULL,
                    history VARCHAR(512) NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE routing_retry_rules (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    normalized_category VARCHAR(64) NOT NULL UNIQUE,
                    retryable BOOLEAN NOT NULL,
                    delay_seconds INT NOT NULL,
                    max_attempts INT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE routing_decision_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version_no VARCHAR(32) NULL,
                    tenant_id BIGINT NULL,
                    carrier VARCHAR(32) NULL,
                    prefix VARCHAR(16) NULL,
                    content_keyword VARCHAR(64) NULL,
                    target_type VARCHAR(32) NOT NULL,
                    target_ref VARCHAR(128) NOT NULL,
                    matched_rule_id BIGINT NULL,
                    explanation VARCHAR(512) NOT NULL,
                    circuit_status VARCHAR(32) NOT NULL,
                    retry_policy VARCHAR(128) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        service = new RoutingCircuitPolicyService(jdbc);
    }

    @Test
    void orderedRulesMatchDeterministicallyAndDefaultHandlesNoMatch() {
        importPolicy(List.of(
                new PolicyRule(20, "CARRIER", "MOBILE", "CHANNEL", "CH_SLOW", 10),
                new PolicyRule(10, "TENANT", "42", "CHANNEL", "CH_TENANT", 100)));

        var tenant = service.simulate(new SimulationRequest(42L, "MOBILE", "139", "hello", "FAILURE"));
        var fallback = service.simulate(new SimulationRequest(99L, "UNICOM", "130", "hello", "FAILURE"));

        assertThat(tenant.targetRef()).isEqualTo("CH_TENANT");
        assertThat(tenant.explanation()).contains("version RP20260909");
        assertThat(fallback.targetType()).isEqualTo("DEFAULT");
        assertThat(fallback.explanation()).contains("no-match default");
    }

    @Test
    void openCircuitExcludesChannelAndPreservesDecisionHistory() {
        importPolicy(List.of(
                new PolicyRule(10, "CARRIER", "MOBILE", "CHANNEL", "CH_A", 90),
                new PolicyRule(20, "CARRIER", "MOBILE", "CHANNEL", "CH_B", 80)));
        service.recordCircuit("CH_A", false, 800);
        service.recordCircuit("CH_A", false, 900);
        service.recordCircuit("CH_A", false, 1000);

        var result = service.simulate(new SimulationRequest(1L, "MOBILE", "139", "hello", "FAILURE"));

        assertThat(result.targetRef()).isEqualTo("CH_B");
        assertThat(service.circuitStates()).anyMatch(row -> row.channelCode().equals("CH_A") && row.status().equals("OPEN"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing_decision_history", Integer.class)).isEqualTo(1);
    }

    @Test
    void retryPolicyMapsNormalizedErrorClassToRetryabilityDelayAndAttempts() {
        service.saveRetryPolicy(new RetryPolicy("UNKNOWN_REVIEW_REQUIRED", false, 0, 0, "ACTIVE"));

        var explicit = service.retryPolicy("UNKNOWN_REVIEW_REQUIRED");
        var defaultFailure = service.retryPolicy("FAILURE");

        assertThat(explicit.retryable()).isFalse();
        assertThat(explicit.maxAttempts()).isZero();
        assertThat(defaultFailure.retryable()).isTrue();
        assertThat(defaultFailure.maxAttempts()).isEqualTo(3);
    }

    @Test
    void schemaPreservesVersionedConditionsTargetsWeightsStateAndHistory() {
        importPolicy(List.of(new PolicyRule(10, "PREFIX", "139", "WEIGHT", "CH_A:70,CH_B:30", 70)));
        service.recordCircuit("CH_A", true, 120);
        service.simulate(new SimulationRequest(7L, "MOBILE", "139", "sale keyword", "FAILURE"));

        assertThat(jdbc.queryForList("SELECT condition_type FROM routing_policy_rules", String.class)).containsExactly("PREFIX");
        assertThat(jdbc.queryForList("SELECT target_type FROM routing_policy_rules", String.class)).containsExactly("WEIGHT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing_circuit_states WHERE history LIKE '%success%'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing_decision_history WHERE version_no='RP20260909'", Integer.class)).isEqualTo(1);
    }

    private void importPolicy(List<PolicyRule> rules) {
        service.importPolicy(new PolicyImportRequest("RP20260909", "运营策略", LocalDateTime.now(), rules), "operator");
    }
}

