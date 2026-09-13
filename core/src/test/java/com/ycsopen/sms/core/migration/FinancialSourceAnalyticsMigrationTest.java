package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialSourceAnalyticsMigrationTest {
    @Test
    void phase39MigrationRegistersFinancialSourceMetric() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase39_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE statistics_metric_registry(
                      metric_code VARCHAR(64) PRIMARY KEY,
                      metric_name VARCHAR(128) NOT NULL,
                      source_tables VARCHAR(255) NOT NULL,
                      formula VARCHAR(255) NOT NULL,
                      freshness_rule VARCHAR(128) NOT NULL,
                      permission_scope VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
                      formula_version VARCHAR(32) NOT NULL DEFAULT 'v1',
                      status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            String migration = new ClassPathResource("db/migration/V4800__financial_source_analytics.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(count(connection, "SELECT COUNT(*) FROM statistics_metric_registry WHERE metric_code='FINANCIAL_SOURCE'"))
                    .isEqualTo(1);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM statistics_metric_registry WHERE metric_code='FINANCIAL_SOURCE' "
                            + "AND source_tables LIKE '%message_tasks%' AND source_tables LIKE '%tenant_price_books%'"))
                    .isEqualTo(1);
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
