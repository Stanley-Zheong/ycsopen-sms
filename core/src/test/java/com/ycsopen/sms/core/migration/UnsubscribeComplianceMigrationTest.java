package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class UnsubscribeComplianceMigrationTest {
    @Test
    void phase33MigrationExtendsLegacyUnsubscribeTablesWithoutDroppingProtectedMobile() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase33_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            var statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE unsubscribe_records (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      mobile_encrypted VARBINARY(255) NOT NULL,
                      mobile_hash CHAR(64) NOT NULL,
                      unsubscribed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      trigger_keyword VARCHAR(32),
                      tenant_id BIGINT NOT NULL,
                      signature_id BIGINT,
                      result VARCHAR(32) NOT NULL DEFAULT 'TENANT_BLACKLISTED',
                      confirmed_reply_sent BOOLEAN NOT NULL DEFAULT FALSE,
                      notified_tenant BOOLEAN NOT NULL DEFAULT FALSE,
                      uplink_record_id BIGINT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE unsubscribe_keywords (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      keyword VARCHAR(32) NOT NULL,
                      scope VARCHAR(16) NOT NULL DEFAULT 'GLOBAL',
                      tenant_id BIGINT,
                      status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                    )
                    """);
            statement.execute("""
                    INSERT INTO unsubscribe_records(mobile_encrypted, mobile_hash, trigger_keyword, tenant_id)
                    VALUES (X'010203', 'legacy-hash', 'TD', 7)
                    """);

            String migration = new ClassPathResource("db/migration/V4200__unsubscribe_compliance.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 33 unsubscribe abnormal rate alert evidence'", "");
            assertThat(migration).doesNotContain("CREATE TABLE unsubscribe_records");
            assertThat(migration).doesNotContain("CREATE TABLE unsubscribe_keywords");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    executeMigrationSql(connection, sql);
                }
            }

            assertThat(hasColumn(connection, "unsubscribe_records", "mobile_encrypted")).isTrue();
            assertThat(hasColumn(connection, "unsubscribe_records", "masked_mobile")).isTrue();
            assertThat(hasColumn(connection, "unsubscribe_records", "notification_state")).isTrue();
            assertThat(hasColumn(connection, "unsubscribe_keywords", "keyword_normalized")).isTrue();
            assertThat(hasColumn(connection, "unsubscribe_keywords", "scope_key")).isTrue();
            assertThat(hasColumn(connection, "unsubscribe_alert_events", "formula")).isTrue();
            assertThat(statement.executeQuery("SELECT mobile_encrypted FROM unsubscribe_records WHERE id=1").next()).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM unsubscribe_keywords WHERE scope_key='GLOBAL'"))
                    .isEqualTo(4);
            assertThat(count(connection, "SELECT COUNT(*) FROM unsubscribe_keywords WHERE keyword_normalized='UNSUBSCRIBE'"))
                    .isEqualTo(1);
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

    private static void executeMigrationSql(Connection connection, String sql) throws Exception {
        String normalized = sql.trim();
        if (normalized.startsWith("ALTER TABLE unsubscribe_keywords") && normalized.contains("ADD COLUMN")) {
            addColumns(connection, "unsubscribe_keywords", normalized);
            return;
        }
        if (normalized.startsWith("ALTER TABLE unsubscribe_records") && normalized.contains("ADD COLUMN")) {
            addColumns(connection, "unsubscribe_records", normalized);
            return;
        }
        if (normalized.startsWith("ALTER TABLE unsubscribe_keywords") && normalized.contains("MODIFY")) {
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
}
