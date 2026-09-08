package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.service.channel.*;
import com.ycsopen.sms.core.web.dto.ChannelConfigurationRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Phase10ChannelDataMySqlTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Phase10ChannelDataMySqlTest {
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
    @Autowired ChannelConfigurationService configurations;
    @Autowired ChannelConfigurationVersionService versions;
    @Autowired ChannelDependencyInventoryService inventory;
    @Autowired ChannelRetirementService retirements;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM channel_configuration_versions");
        jdbc.update("DELETE FROM route_rules WHERE rule_name LIKE 'phase10-%'");
        jdbc.update("DELETE FROM channel_group_members WHERE group_id IN (9010,9011)");
        jdbc.update("DELETE FROM signature_channel_registrations WHERE signature_id IN (9010,9011)");
        jdbc.update("DELETE FROM message_tasks WHERE message_id LIKE 'phase10-%'");
        jdbc.update("DELETE FROM channels WHERE channel_name LIKE 'phase10-%'");
    }

    @Test
    void mysqlPreservesProtectedChannelDataVersionAndOfflineFacts() {
        long source = configurations.create(7L, request("phase10-source", 7890)).id();
        long destination = configurations.create(7L, request("phase10-destination", 7891)).id();

        var activated = versions.activate(7L, source, null);

        assertThat(activated.resultCode()).isEqualTo("EFFECTIVE");
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT channel_name, protocol, operator, host, port, account_encrypted, password_encrypted,
                       sp_id, service_id, src_id, max_connections, window_size, price, priority,
                       tps_limit, active_window, availability,
                       JSON_EXTRACT(extra_config, '$.carrierProvince') AS province,
                       effective_version_id, configuration_version, status
                  FROM channels WHERE id=?
                """, source);
        assertThat(row.get("channel_name")).isEqualTo("phase10-source");
        assertThat(row.get("protocol")).isEqualTo("CMPP");
        assertThat(row.get("operator")).isEqualTo("MOBILE");
        assertThat(row.get("host")).isEqualTo("channel-fixture.local");
        assertThat(((Number) row.get("port")).intValue()).isEqualTo(7890);
        assertThat(((BigDecimal) row.get("price"))).isEqualByComparingTo("0.0123");
        assertThat(((Number) row.get("priority")).intValue()).isEqualTo(50);
        assertThat(((Number) row.get("tps_limit")).intValue()).isEqualTo(100);
        assertThat(row.get("availability")).isEqualTo("AVAILABLE");
        assertThat(row.get("province").toString()).contains("CN-FJ");
        assertThat(row.get("effective_version_id")).isNotNull();
        assertThat(row.get("status")).isEqualTo("NORMAL");
        assertThat(new String((byte[]) row.get("account_encrypted"), StandardCharsets.UTF_8)).doesNotContain("account-a");
        assertThat(new String((byte[]) row.get("password_encrypted"), StandardCharsets.UTF_8)).doesNotContain("Secret");

        jdbc.update("INSERT INTO route_rules(rule_name,target_channel_id,status) VALUES ('phase10-route',?,'ACTIVE')", source);
        var blocked = retirements.offline(7L, source);
        assertThat(blocked.changed()).isFalse();
        assertThat(blocked.unresolvedDependencies()).singleElement()
                .satisfies(item -> assertThat(item.source()).isEqualTo("ROUTE_RULE"));

        jdbc.update("UPDATE route_rules SET target_channel_id=? WHERE target_channel_id=?", destination, source);
        assertThat(inventory.inventory(source)).isEmpty();
        assertThat(retirements.offline(7L, source).status()).isEqualTo("OFFLINE");
        assertThat(jdbc.queryForObject("SELECT offline_by FROM channels WHERE id=?", String.class, source))
                .isEqualTo("7");
    }

    private ChannelConfigurationRequest request(String name, int port) {
        return new ChannelConfigurationRequest(name, "CMPP", "MOBILE", "channel-fixture.local",
                port, "account-a", "Secret-123", "SPID10", "svc10", "SRC10", 4, 8, 100,
                BigDecimal.valueOf(0.01234), null, "00:00-23:59", "AVAILABLE",
                Map.of("carrierProvince", "CN-FJ"), null);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    @Import({ChannelConfigurationService.class, ChannelConnectivityAdapter.class,
            ChannelConfigurationVersionService.class, ChannelConfigurationSnapshotRegistry.class,
            ChannelDependencyInventoryService.class, ChannelRetirementService.class,
            OperationAuditService.class, Dependencies.class})
    static class Application { }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean
        ChannelSecretProtector protector() {
            return (channelId, field, value) -> {
                byte[] protectedValue = ("mysql-enc:" + channelId + ":" + field).getBytes(StandardCharsets.UTF_8);
                java.util.Arrays.fill(value, '\0');
                return protectedValue;
            };
        }
    }
}
