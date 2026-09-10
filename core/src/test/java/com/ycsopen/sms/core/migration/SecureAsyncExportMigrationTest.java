package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class SecureAsyncExportMigrationTest {
    @Test
    void phase46MigrationExtendsExportTasksAndCreatesPermissions() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase46_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE export_tasks(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      export_type VARCHAR(64) NOT NULL,
                      created_by VARCHAR(64),
                      file_format VARCHAR(16) NOT NULL,
                      status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                      progress_pct INT NOT NULL DEFAULT 0,
                      record_count BIGINT NOT NULL DEFAULT 0,
                      file_size_bytes BIGINT,
                      file_url VARCHAR(255),
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
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
            String migration = new ClassPathResource("db/migration/V5500__secure_async_export.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" JSON", " VARCHAR(4000)")
                    .replace(" LONGBLOB", " BLOB")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replaceAll(" AFTER [a-z0-9_]+", "")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll(",\\s*ADD KEY[^;]+", "");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    String normalized = sql.trim();
                    if (normalized.startsWith("ALTER TABLE export_tasks")) {
                        for (String line : normalized.split("\\n")) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("ADD COLUMN")) {
                                connection.createStatement().execute("ALTER TABLE export_tasks "
                                        + trimmed.replaceAll(",$", ""));
                            } else if (trimmed.startsWith("ADD UNIQUE KEY")) {
                                connection.createStatement().execute("""
                                        ALTER TABLE export_tasks
                                        ADD CONSTRAINT uk_export_tasks_request UNIQUE (request_id)
                                        """);
                            }
                        }
                    } else {
                        connection.createStatement().execute(normalized);
                    }
                }
            }

            assertThat(hasColumn(connection, "export_tasks", "request_id")).isTrue();
            assertThat(hasColumn(connection, "export_tasks", "authorization_snapshot")).isTrue();
            assertThat(hasColumn(connection, "export_tasks", "artifact_ciphertext")).isTrue();
            assertThat(hasColumn(connection, "export_tasks", "download_token_hash")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM permissions WHERE permission_code LIKE 'secure-async-export:%'"))
                    .isEqualTo(5);
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
