package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEngineMigrationTest {
    @Test
    void phase35MigrationExtendsAlertTablesAndCreatesDeliveryEvidence() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase35_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            var statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE alert_rules(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      rule_name VARCHAR(128) NOT NULL,
                      rule_type VARCHAR(32) NOT NULL,
                      metric_name VARCHAR(64) NOT NULL,
                      threshold_value DECIMAL(12,4) NOT NULL,
                      comparison_op VARCHAR(8) NOT NULL DEFAULT '>=',
                      duration_minutes INT NOT NULL DEFAULT 5,
                      severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
                      notify_channels VARCHAR(1000),
                      status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                    )
                    """);
            statement.execute("""
                    CREATE TABLE alert_records(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      rule_id BIGINT NOT NULL,
                      title VARCHAR(255) NOT NULL,
                      content VARCHAR(1000),
                      metric_value DECIMAL(12,4),
                      status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                      triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      acknowledged_at TIMESTAMP,
                      resolved_at TIMESTAMP
                    )
                    """);

            String migration = new ClassPathResource("db/migration/V4400__alert_engine_console.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" JSON", " VARCHAR(1000)")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    executeMigrationSql(connection, sql);
                }
            }

            assertThat(hasColumn(connection, "alert_rules", "metric_source")).isTrue();
            assertThat(hasColumn(connection, "alert_rules", "notification_targets")).isTrue();
            assertThat(hasColumn(connection, "alert_records", "severity")).isTrue();
            assertThat(hasColumn(connection, "alert_records", "source_key")).isTrue();
            assertThat(hasColumn(connection, "alert_records", "acknowledged_by")).isTrue();
            assertThat(hasColumn(connection, "alert_records", "resolved_by")).isTrue();
            assertThat(hasColumn(connection, "alert_records", "delivery_state")).isTrue();
            assertThat(hasTable(connection, "alert_delivery_attempts")).isTrue();
            assertThat(hasTable(connection, "alert_mutes")).isTrue();
        }
    }

    private static void executeMigrationSql(Connection connection, String sql) throws Exception {
        String normalized = sql.trim();
        if (normalized.startsWith("ALTER TABLE alert_rules") && normalized.contains("ADD COLUMN")) {
            addColumns(connection, "alert_rules", normalized);
            return;
        }
        if (normalized.startsWith("ALTER TABLE alert_records") && normalized.contains("ADD COLUMN")) {
            addColumns(connection, "alert_records", normalized);
            return;
        }
        connection.createStatement().execute(normalized);
    }

    private static void addColumns(Connection connection, String table, String sql) throws Exception {
        for (String line : sql.split("\\R")) {
            String column = line.trim();
            if (!column.startsWith("ADD COLUMN")) {
                continue;
            }
            column = column.replaceFirst(",$", "");
            connection.createStatement().execute("ALTER TABLE " + table + " " + column);
        }
    }

    private static boolean hasTable(Connection connection, String table) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}
