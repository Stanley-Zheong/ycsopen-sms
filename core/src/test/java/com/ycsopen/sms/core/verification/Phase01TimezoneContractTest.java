package com.ycsopen.sms.core.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OBL-FOUND-TRACE-003 and DR-01-008: reusable UTC+8/IANA verifier only.
 * This synthetic DTO and disposable table are not product persistence evidence.
 */
@SpringBootTest(
        classes = Phase01TimezoneContractTest.TimezoneVerificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase01TimezoneContractTest {

    private static final TimeZone ORIGINAL_DEFAULT = TimeZone.getDefault();

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }

    private static Phase01ServiceSession mysql;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        mysql = Phase01ServiceHarness.startMySql();
        registry.add("spring.datasource.url", mysql::jdbcUrl);
        registry.add("spring.datasource.username", mysql::username);
        registry.add("spring.datasource.password", mysql::password);
        registry.add("spring.jackson.time-zone", () -> "Asia/Shanghai");
    }

    @AfterAll
    static void restoreDefaultsAndStopMySql() {
        try {
            if (mysql != null) {
                mysql.close();
            }
        } finally {
            TimeZone.setDefault(ORIGINAL_DEFAULT);
        }
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void retainsInstantOffsetAndIanaZoneUnderANonShanghaiJvmDefault() throws Exception {
        JsonNode fixture = fixture();
        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/New_York");

        ZoneId zone = ZoneId.of(fixture.path("iana_zone").asText());
        Instant instant = Instant.parse(fixture.path("expected_instant").asText());
        ZonedDateTime shanghai = instant.atZone(zone);
        assertThat(shanghai.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"));
        assertThat(shanghai.getOffset()).isEqualTo(ZoneOffset.of(fixture.path("expected_offset").asText()));

        SyntheticInternationalTime value = new SyntheticInternationalTime(
                zone.getId(), instant, shanghai.getOffset(), shanghai.toLocalDateTime());
        String serialized = objectMapper.writeValueAsString(value);
        SyntheticInternationalTime restored = objectMapper.readValue(serialized, SyntheticInternationalTime.class);
        assertThat(restored).isEqualTo(value);
        assertThat(restored.ianaZone()).isEqualTo("Asia/Shanghai");
        assertThat(restored.instant()).isEqualTo(instant);
        assertThat(restored.offset()).isEqualTo(ZoneOffset.of("+08:00"));
    }

    @Test
    void usesAnExplicitShanghaiMysqlSessionForTemporalRoundTrips() {
        assertThat(jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class))
                .isEqualTo("Asia/Shanghai");

        String table = "phase01_verification_timezone";
        Instant expectedInstant = Instant.parse("2024-02-29T16:00:00Z");
        LocalDateTime expectedLocal = LocalDateTime.parse("2024-03-01T00:00:00");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
        jdbcTemplate.execute("CREATE TABLE " + table +
                " (id BIGINT PRIMARY KEY, event_instant TIMESTAMP(6), event_local DATETIME(6)) " +
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO " + table + " (id, event_instant, event_local) VALUES (?, ?, ?)",
                        1L, Timestamp.from(expectedInstant), expectedLocal);
                Timestamp instantValue = jdbcTemplate.queryForObject(
                        "SELECT event_instant FROM " + table + " WHERE id = 1", Timestamp.class);
                LocalDateTime localValue = jdbcTemplate.queryForObject(
                        "SELECT event_local FROM " + table + " WHERE id = 1", LocalDateTime.class);
                assertThat(instantValue).isNotNull();
                assertThat(instantValue.toInstant()).isEqualTo(expectedInstant);
                assertThat(localValue).isEqualTo(expectedLocal);
                status.setRollbackOnly();
            });
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
        }
    }

    private JsonNode fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/verification/timezone-contract.json")) {
            assertThat(input).isNotNull();
            JsonNode fixture = objectMapper.readTree(input);
            assertThat(fixture.path("schema_version").asText()).isEqualTo("phase01-timezone-v1");
            assertThat(fixture.path("serialized_contract").path("iana_zone").asText())
                    .isEqualTo("Asia/Shanghai");
            return fixture;
        }
    }

    record SyntheticInternationalTime(
            String ianaZone,
            Instant instant,
            ZoneOffset offset,
            LocalDateTime localDateTime
    ) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    static class TimezoneVerificationApplication {
    }
}
