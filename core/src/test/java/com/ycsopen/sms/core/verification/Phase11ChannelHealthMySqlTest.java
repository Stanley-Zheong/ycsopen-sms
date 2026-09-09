package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.service.channel.health.ChannelCandidateEligibilityService;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthService;
import com.ycsopen.sms.core.service.channel.health.ChannelPoolService;
import com.ycsopen.sms.core.web.dto.ChannelHealthObservationRequest;
import com.ycsopen.sms.core.web.dto.ChannelPauseRequest;
import com.ycsopen.sms.core.web.dto.ChannelPoolRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Phase11ChannelHealthMySqlTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Phase11ChannelHealthMySqlTest {
    private static Phase03ServiceHarness.ServiceSession mysql;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        mysql = Phase03ServiceHarness.startMySql();
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + mysql.host() + ":" + mysql.port()
                + "/phase01?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");
        registry.add("spring.datasource.username", mysql::username);
        registry.add("spring.datasource.password", mysql::password);
        registry.add("spring.flyway.user", mysql::username);
        registry.add("spring.flyway.password", mysql::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @AfterAll
    static void stop() {
        if (mysql != null) mysql.close();
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ChannelHealthService health;
    @Autowired ChannelPoolService pools;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM channel_pool_members");
        jdbc.update("DELETE FROM channel_pools WHERE pool_name LIKE 'phase11-%'");
        jdbc.update("DELETE FROM channel_pause_events");
        jdbc.update("DELETE FROM channel_health_observations");
        jdbc.update("DELETE FROM channels WHERE channel_name LIKE 'phase11-%'");
    }

    @Test
    void mysqlPersistsHealthPauseMaintenanceAndPoolFacts() {
        long primary = seedChannel("phase11-primary", Channel.Status.NORMAL, 9101);
        long backup = seedChannel("phase11-backup", Channel.Status.NORMAL, 9102);

        health.recordObservation(primary, sample(false, "TIMEOUT"));
        var failed = health.recordObservation(primary, sample(false, "TIMEOUT"));

        assertThat(failed.status()).isEqualTo("MAINTENANCE");
        assertThat(failed.candidateEligible()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_health_observations WHERE channel_id=?",
                Integer.class, primary)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_pause_events WHERE event_type='HEALTH_FAILURE'",
                Integer.class)).isOne();

        assertThatThrownBy(() -> health.endMaintenance(primary,
                new ChannelPauseRequest("MANUAL", "operator", "end")))
                .hasMessageContaining("健康验证");

        health.recordObservation(primary, sample(true, "OK"));
        assertThat(health.endMaintenance(primary, new ChannelPauseRequest("MANUAL", "operator", "end")).status())
                .isEqualTo("NORMAL");

        var paused = health.pause(primary, new ChannelPauseRequest("RATIO", "operator", "投诉占比超阈值"));
        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(paused.candidateReasonCode()).isEqualTo("STATUS_PAUSED");
        assertThat(health.resume(primary, "operator").status()).isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM channel_pause_events
                 WHERE channel_id=? AND event_type='RESUME'
                """, Integer.class, primary)).isOne();

        var pool = pools.create(new ChannelPoolRequest("phase11-weighted", "WEIGHTED", null, List.of(
                new ChannelPoolRequest.Member(backup, 100, false, true),
                new ChannelPoolRequest.Member(primary, 0, false, false)
        )));
        assertThat(pool.members()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_pool_members WHERE enabled=0",
                Integer.class)).isOne();
    }

    private long seedChannel(String name, Channel.Status status, int port) {
        jdbc.update("""
                INSERT INTO channels(channel_name, protocol, operator, host, port, max_connections,
                                     window_size, tps_limit, price, priority, active_window, availability,
                                     extra_config, effective_version_id, configuration_version, status)
                VALUES (?, 'CMPP', 'MOBILE', 'channel-fixture.local', ?, 4, 8, 100,
                        0.0123, 50, '00:00-23:59', 'AVAILABLE', JSON_OBJECT(), 1, 1, ?)
                """, name, port, status.name());
        return jdbc.queryForObject("SELECT id FROM channels WHERE channel_name=?", Long.class, name);
    }

    private ChannelHealthObservationRequest sample(boolean connected, String reason) {
        return new ChannelHealthObservationRequest(connected, new BigDecimal("0.0100"),
                connected ? new BigDecimal("0.0200") : new BigDecimal("0.5000"), 120L, reason);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    @EntityScan("com.ycsopen.sms.core.domain.entity")
    @EnableJpaRepositories("com.ycsopen.sms.core.repository")
    @Import({ChannelHealthService.class, ChannelPoolService.class, ChannelCandidateEligibilityService.class})
    static class Application { }
}
