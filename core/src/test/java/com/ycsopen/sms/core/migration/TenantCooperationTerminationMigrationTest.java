package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class TenantCooperationTerminationMigrationTest {
    @Test
    void phase49MigrationCreatesTerminationTablesAndPermissions() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase49_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
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
            String migration = new ClassPathResource("db/migration/V5800__tenant_cooperation_termination.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" JSON", " VARCHAR(8000)")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replace("PRIMARY KEY AUTO_INCREMENT", "AUTO_INCREMENT PRIMARY KEY")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("(?m)^\\s+UNIQUE KEY [^\\n]+\\n", "")
                    .replaceAll("(?m)^\\s+KEY [^\\n]+\\n", "")
                    .replaceAll(",\\s*\\)", ")")
                    .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']+'", "");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasTable(connection, "tenant_termination_requests")).isTrue();
            assertThat(hasTable(connection, "tenant_termination_participants")).isTrue();
            assertThat(hasTable(connection, "tenant_termination_audits")).isTrue();
            assertThat(hasColumn(connection, "tenant_termination_requests", "clearance_snapshot_json")).isTrue();
            assertThat(hasColumn(connection, "tenant_termination_participants", "participant_code")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM permissions WHERE permission_code LIKE 'tenant-termination:%'"))
                    .isEqualTo(5);
        }
    }

    private static boolean hasTable(Connection connection, String table) throws Exception {
        try (var rows = connection.getMetaData().getTables(null, null, table, null)) {
            return rows.next();
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (var rows = connection.getMetaData().getColumns(null, null, table, column)) {
            return rows.next();
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
