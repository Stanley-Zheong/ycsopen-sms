package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRiskAutoPauseMigrationTest {
    @Test
    void migrationExtendsTenantRulesAndCreatesEpisodeEvidence() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase42_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            var statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE tenant_alert_rules(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      rule_name VARCHAR(100) NOT NULL,
                      metric VARCHAR(32) NOT NULL,
                      threshold_value DECIMAL(6,4) NOT NULL,
                      duration_minutes INT NOT NULL DEFAULT 60,
                      action VARCHAR(32) NOT NULL DEFAULT 'NOTIFY',
                      notify_targets VARCHAR(1000)
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
                      severity VARCHAR(16) NOT NULL DEFAULT 'HIGH',
                      source_module VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
                      source_key VARCHAR(128) NOT NULL DEFAULT 'UNKNOWN',
                      impact_scope VARCHAR(255),
                      delivery_state VARCHAR(32) NOT NULL DEFAULT 'DELIVERED'
                    )
                    """);

            String migration = new ClassPathResource("db/migration/V5100__tenant_risk_auto_pause.sql")
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

            assertThat(hasColumn(connection, "tenant_alert_rules", "tenant_id")).isTrue();
            assertThat(hasColumn(connection, "tenant_alert_rules", "status")).isTrue();
            assertThat(hasColumn(connection, "tenant_alert_rules", "created_by")).isTrue();
            assertThat(hasTable(connection, "tenant_risk_episodes")).isTrue();
            assertThat(hasColumn(connection, "tenant_risk_episodes", "source_snapshot")).isTrue();
            assertThat(hasColumn(connection, "tenant_risk_episodes", "data_quality")).isTrue();
            assertThat(hasColumn(connection, "tenant_risk_episodes", "recovery_review_id")).isTrue();
        }
    }

    private static void executeMigrationSql(Connection connection, String sql) throws Exception {
        String normalized = sql.trim();
        if (normalized.startsWith("ALTER TABLE tenant_alert_rules") && normalized.contains("ADD COLUMN")) {
            addColumns(connection, normalized);
            return;
        }
        connection.createStatement().execute(normalized);
    }

    private static void addColumns(Connection connection, String sql) throws Exception {
        for (String line : sql.split("\\R")) {
            String column = line.trim();
            if (!column.startsWith("ADD COLUMN")) {
                continue;
            }
            column = column.replaceFirst(",$", "");
            connection.createStatement().execute("ALTER TABLE tenant_alert_rules " + column);
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
