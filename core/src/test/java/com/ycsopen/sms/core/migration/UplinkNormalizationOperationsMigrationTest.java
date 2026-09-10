package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class UplinkNormalizationOperationsMigrationTest {
    @Test
    void phase32MigrationExtendsLegacyUplinkRecordsWithoutDroppingProtectedMobile() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase32_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            var statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE uplink_records (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      tenant_id BIGINT,
                      mobile_encrypted VARBINARY(255) NOT NULL,
                      content VARCHAR(500) NOT NULL,
                      target_number VARCHAR(32),
                      channel_id BIGINT,
                      location VARCHAR(64),
                      is_unsubscribe BOOLEAN NOT NULL DEFAULT FALSE,
                      push_status VARCHAR(16) NOT NULL DEFAULT 'NOT_PUSHED',
                      push_time TIMESTAMP,
                      push_url VARCHAR(255),
                      received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT INTO uplink_records(tenant_id, mobile_encrypted, content, target_number, channel_id,
                        location, push_status, push_time, received_at)
                    VALUES (7, X'010203', '回复帮助', '10690000', 2, '杭州', 'FAILED',
                        TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 07:59:00')
                    """);

            String migration = new ClassPathResource("db/migration/V4100__uplink_normalization_operations.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace("SHA2(CONCAT('legacy-uplink:', id), 256)", "CONCAT('legacy-hash-', id)")
                    .replace("MODIFY COLUMN", "ALTER COLUMN");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    executeMigrationSql(connection, sql);
                }
            }

            assertThat(hasColumn(connection, "uplink_records", "mobile_encrypted")).isTrue();
            assertThat(hasColumn(connection, "uplink_records", "source_protocol")).isTrue();
            assertThat(hasColumn(connection, "uplink_records", "source_connector")).isTrue();
            assertThat(hasColumn(connection, "uplink_records", "source_event_id")).isTrue();
            assertThat(hasColumn(connection, "uplink_records", "phone_hash")).isTrue();
            assertThat(hasColumn(connection, "tenant_uplink_auto_reply_configs", "loop_guard_minutes")).isTrue();
            assertThat(hasColumn(connection, "tenant_uplink_auto_reply_attempts", "decision")).isTrue();

            var row = statement.executeQuery("""
                    SELECT source_protocol, source_connector, source_event_id, phone_masked, phone_hash,
                           content_keyword, destination, city, push_state, receive_time
                      FROM uplink_records
                     WHERE id=1
                    """);
            assertThat(row.next()).isTrue();
            assertThat(row.getString("source_protocol")).isEqualTo("LEGACY");
            assertThat(row.getString("source_connector")).isEqualTo("V1_UPLINK");
            assertThat(row.getString("source_event_id")).isEqualTo("LEGACY-1");
            assertThat(row.getString("phone_masked")).isEqualTo("legacy-protected");
            assertThat(row.getString("phone_hash")).isEqualTo("legacy-hash-1");
            assertThat(row.getString("content_keyword")).isEqualTo("回复帮助");
            assertThat(row.getString("destination")).isEqualTo("10690000");
            assertThat(row.getString("city")).isEqualTo("杭州");
            assertThat(row.getString("push_state")).isEqualTo("PUSH_FAILED");
            assertThat(row.getTimestamp("receive_time")).isNotNull();
        }
    }

    private static boolean hasColumn(java.sql.Connection connection, String table, String column) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    private static void executeMigrationSql(java.sql.Connection connection, String sql) throws Exception {
        String normalized = sql.trim();
        if (normalized.startsWith("ALTER TABLE uplink_records") && normalized.contains("ADD COLUMN")) {
            for (String line : normalized.split("\\R")) {
                String column = line.trim();
                if (!column.startsWith("ADD COLUMN")) {
                    continue;
                }
                column = column.replaceFirst(",$", "")
                        .replaceFirst("\\s+AFTER\\s+[a-z_]+$", "");
                connection.createStatement().execute("ALTER TABLE uplink_records " + column);
            }
            return;
        }
        if (normalized.startsWith("ALTER TABLE uplink_records") && normalized.contains("ALTER COLUMN")) {
            for (String line : normalized.split("\\R")) {
                String column = line.trim();
                if (!column.startsWith("ALTER COLUMN")) {
                    continue;
                }
                connection.createStatement().execute("ALTER TABLE uplink_records " + column.replaceFirst(",$", ""));
            }
            return;
        }
        connection.createStatement().execute(normalized);
    }
}
