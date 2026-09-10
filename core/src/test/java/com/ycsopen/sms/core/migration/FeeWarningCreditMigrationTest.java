package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FeeWarningCreditMigrationTest {
    @Test
    void phase40MigrationCreatesRulesEpisodesAndDefaultAlertRule() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase40_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE alert_rules(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      rule_name VARCHAR(128) NOT NULL,
                      rule_type VARCHAR(32) NOT NULL,
                      metric_name VARCHAR(64) NOT NULL,
                      metric_source VARCHAR(64) NOT NULL,
                      threshold_value DECIMAL(12,4) NOT NULL,
                      comparison_op VARCHAR(8) NOT NULL,
                      duration_minutes INT NOT NULL,
                      severity VARCHAR(16) NOT NULL,
                      notify_channels VARCHAR(1000),
                      notification_targets VARCHAR(1000),
                      source_scope VARCHAR(64) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      created_by VARCHAR(64),
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            String migration = new ClassPathResource("db/migration/V4900__fee_warning_credit_enforcement.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" JSON", " VARCHAR(1000)")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasTable(connection, "fee_warning_rules")).isTrue();
            assertThat(hasTable(connection, "fee_warning_episodes")).isTrue();
            assertThat(hasColumn(connection, "fee_warning_rules", "notification_targets")).isTrue();
            assertThat(hasColumn(connection, "fee_warning_episodes", "approval_state")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM alert_rules WHERE rule_type='FEE_WARNING'"))
                    .isEqualTo(1);
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

    private static int count(Connection connection, String sql) throws Exception {
        try (var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
