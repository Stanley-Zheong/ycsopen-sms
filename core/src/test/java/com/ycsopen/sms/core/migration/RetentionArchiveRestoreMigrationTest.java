package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionArchiveRestoreMigrationTest {
    @Test
    void phase47MigrationCreatesArchiveTablesPoliciesAndPermissions() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase47_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
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
            String migration = new ClassPathResource("db/migration/V5600__retention_archive_restore.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" JSON", " VARCHAR(4000)")
                    .replace(" LONGBLOB", " BLOB")
                    .replace("TINYINT(1)", "BOOLEAN")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("(?is)ON DUPLICATE KEY UPDATE[^;]+", "");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasTable(connection, "archive_policies")).isTrue();
            assertThat(hasTable(connection, "archive_manifests")).isTrue();
            assertThat(hasTable(connection, "archive_restore_jobs")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM archive_policies")).isEqualTo(8);
            assertThat(count(connection, "SELECT COUNT(*) FROM permissions WHERE permission_code LIKE 'retention-archive:%'"))
                    .isEqualTo(6);
        }
    }

    private static boolean hasTable(Connection connection, String table) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
