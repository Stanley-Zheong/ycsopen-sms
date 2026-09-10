package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class CustomReportAuthoringMigrationTest {
    @Test
    void phase43MigrationCreatesDefinitionAndExportRequestTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase43_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE permissions(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      permission_code VARCHAR(100) NOT NULL UNIQUE,
                      permission_name VARCHAR(100) NOT NULL,
                      resource_type VARCHAR(50) NOT NULL,
                      resource_path VARCHAR(255),
                      http_method VARCHAR(10),
                      parent_id BIGINT,
                      sort_order INT,
                      status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    )
                    """);
            String migration = new ClassPathResource("db/migration/V5200__custom_report_authoring.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" JSON", " VARCHAR(4000)")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasTable(connection, "custom_report_definitions")).isTrue();
            assertThat(hasTable(connection, "custom_report_export_requests")).isTrue();
            assertThat(hasColumn(connection, "custom_report_definitions", "definition_snapshot")).isTrue();
            assertThat(hasColumn(connection, "custom_report_definitions", "dimensions_json")).isTrue();
            assertThat(hasColumn(connection, "custom_report_definitions", "role_scope")).isTrue();
            assertThat(hasColumn(connection, "custom_report_export_requests", "definition_snapshot")).isTrue();
            assertThat(hasColumn(connection, "custom_report_export_requests", "status")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM permissions WHERE permission_code LIKE 'custom-report:%'"))
                    .isEqualTo(3);
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
