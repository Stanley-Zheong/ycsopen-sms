package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsAggregationMigrationTest {
    @Test
    void phase34MigrationCreatesMetricRegistryAggregateAndCorrectionTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase34_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            String migration = new ClassPathResource("db/migration/V4300__statistics_aggregation_pipeline.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasTable(connection, "statistics_metric_registry")).isTrue();
            assertThat(hasTable(connection, "statistics_aggregates")).isTrue();
            assertThat(hasTable(connection, "statistics_correction_events")).isTrue();
            assertThat(hasColumn(connection, "statistics_aggregates", "bucket_start")).isTrue();
            assertThat(hasColumn(connection, "statistics_aggregates", "source_version")).isTrue();
            assertThat(hasColumn(connection, "statistics_aggregates", "correction_identity")).isTrue();
            assertThat(hasColumn(connection, "statistics_aggregates", "drilldown_key")).isTrue();
            assertThat(hasColumn(connection, "statistics_correction_events", "correction_identity")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM statistics_metric_registry")).isEqualTo(3);
            assertThat(count(connection,
                    "SELECT COUNT(*) FROM statistics_metric_registry WHERE metric_code='CHANNEL_DELIVERY' "
                            + "AND source_tables LIKE '%delivery_reports%' AND source_tables LIKE '%billing_records%'"))
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
