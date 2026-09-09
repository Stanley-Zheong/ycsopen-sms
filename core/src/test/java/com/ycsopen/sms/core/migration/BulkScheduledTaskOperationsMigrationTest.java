package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class BulkScheduledTaskOperationsMigrationTest {
    @Test
    void phase29MigrationExtendsBulkTaskAndItemEvidence() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase29_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE bulk_sendings(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      tenant_id BIGINT NOT NULL,
                      task_name VARCHAR(128) NOT NULL,
                      message_type VARCHAR(32) NOT NULL,
                      priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
                      template_id BIGINT,
                      signature_id BIGINT,
                      total_count INT NOT NULL DEFAULT 0,
                      success_count INT NOT NULL DEFAULT 0,
                      fail_count INT NOT NULL DEFAULT 0,
                      task_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                      schedule_time TIMESTAMP,
                      start_time TIMESTAMP,
                      end_time TIMESTAMP,
                      total_cost DECIMAL(12,4) NOT NULL DEFAULT 0,
                      created_by VARCHAR(64),
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE bulk_sending_items(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      bulk_id BIGINT NOT NULL,
                      mobile_encrypted VARBINARY(255) NOT NULL,
                      template_params VARCHAR(1000),
                      send_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                      send_time TIMESTAMP,
                      error_message VARCHAR(255)
                    )
                    """);
            String migration = new ClassPathResource("db/migration/V3800__bulk_scheduled_task_operations.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("JSON", "VARCHAR(4000)")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replace("MODIFY COLUMN task_status ENUM('PENDING','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLED')", "ALTER COLUMN task_status VARCHAR(24)")
                    .replace("MODIFY COLUMN send_status ENUM('PENDING','SENT','DELIVERED','FAILED','CANCELLED')", "ALTER COLUMN send_status VARCHAR(24)")
                    .replace("ENUM('VALID','INVALID')", "VARCHAR(16)");
            for (String statement : migration.split(";")) {
                if (!statement.isBlank()) {
                    connection.createStatement().execute(statement);
                }
            }

            assertThat(hasColumn(connection, "bulk_sendings", "batch_key")).isTrue();
            assertThat(hasColumn(connection, "bulk_sendings", "valid_count")).isTrue();
            assertThat(hasColumn(connection, "bulk_sendings", "import_snapshot_json")).isTrue();
            assertThat(hasColumn(connection, "bulk_sending_items", "item_tracking_id")).isTrue();
            assertThat(hasColumn(connection, "bulk_sending_items", "message_task_id")).isTrue();
            assertThat(hasColumn(connection, "bulk_sending_items", "validation_status")).isTrue();
        }
    }

    private static boolean hasColumn(java.sql.Connection connection, String table, String column) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}
