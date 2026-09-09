package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class MessageReceiptErrorOperationsMigrationTest {
    @Test
    void phase27MigrationAddsOperationEvidenceTableAndPermissions() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase27_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE permissions(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      permission_code VARCHAR(128) UNIQUE,
                      permission_name VARCHAR(128),
                      resource_type VARCHAR(32),
                      resource_path VARCHAR(255),
                      http_method VARCHAR(16),
                      parent_id BIGINT,
                      sort_order INT,
                      status VARCHAR(16)
                    )
                    """);
            String migration = new ClassPathResource("db/migration/V3600__message_receipt_error_operations.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("JSON", "VARCHAR(1000)")
                    .replace("DATETIME(6)", "TIMESTAMP")
                    .replace("CURRENT_TIMESTAMP(6)", "CURRENT_TIMESTAMP")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase 27 idempotent message, receipt, and error operation evidence'", "")
                    .replace("ENUM('RESEND','APPEAL','RECEIPT_CORRECT','RECEIPT_REPLAY','BULK_RETRY','MARK_PROBLEM','EXPORT_REQUEST')", "VARCHAR(32)")
                    .replace("ENUM('REQUESTED','COMPLETED','FAILED','DUPLICATE')", "VARCHAR(32)")
                    .replace("ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),\n                        resource_type = VALUES(resource_type),\n                        resource_path = VALUES(resource_path),\n                        http_method = VALUES(http_method),\n                        sort_order = VALUES(sort_order),\n                        status = VALUES(status)", "");
            for (String statement : migration.split(";")) {
                if (!statement.isBlank()) {
                    connection.createStatement().execute(statement);
                }
            }

            assertThat(hasTable(connection, "message_operation_events")).isTrue();
            assertThat(hasUniqueIndexOnColumn(connection, "message_operation_events", "operation_key")).isTrue();
            assertThat(permissionCount(connection)).isEqualTo(3);
        }
    }

    private static boolean hasTable(java.sql.Connection connection, String table) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }

    private static boolean hasUniqueIndexOnColumn(java.sql.Connection connection, String table, String column)
            throws Exception {
        try (var indexes = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (indexes.next()) {
                if (!indexes.getBoolean("NON_UNIQUE")
                        && column.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int permissionCount(java.sql.Connection connection) throws Exception {
        try (var result = connection.createStatement()
                .executeQuery("SELECT COUNT(*) FROM permissions WHERE permission_code LIKE 'message-ops:%'")) {
            result.next();
            return result.getInt(1);
        }
    }
}
