package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryTransportMigrationTest {
    @Test
    void migrationCreatesWebhookEventAndAttemptEvidenceTables() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase28-migration;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE tenant_callback_configs(
                  tenant_id BIGINT PRIMARY KEY,
                  delivery_callback_url VARCHAR(255),
                  uplink_callback_url VARCHAR(255),
                  unsubscribe_callback_url VARCHAR(255),
                  retry_max_count INT NOT NULL DEFAULT 5,
                  retry_backoff_seconds INT NOT NULL DEFAULT 30
                )
                """);
        String migration = new ClassPathResource("db/migration/V3700__webhook_delivery_transport.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("BIGINT UNSIGNED", "BIGINT")
                .replace("AUTO_INCREMENT", "AUTO_INCREMENT")
                .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']+'", "");
        for (String statement : migration.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            assertThat(hasColumn(connection, "tenant_callback_configs", "config_status")).isTrue();
            assertThat(hasColumn(connection, "tenant_callback_configs", "latest_failure_reason")).isTrue();
            assertThat(hasColumn(connection, "tenant_callback_configs", "version")).isTrue();
            assertThat(hasColumn(connection, "tenant_callback_configs", "callback_signing_secret")).isTrue();
            assertThat(hasColumn(connection, "webhook_delivery_events", "logical_id")).isTrue();
            assertThat(hasColumn(connection, "webhook_delivery_events", "signature")).isTrue();
            assertThat(hasColumn(connection, "webhook_delivery_attempts", "attempt_no")).isTrue();
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (var rs = connection.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }
}
