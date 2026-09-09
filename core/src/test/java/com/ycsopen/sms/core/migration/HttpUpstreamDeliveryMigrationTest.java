package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class HttpUpstreamDeliveryMigrationTest {
    @Test
    void phase24MigrationAddsClaimProviderAndReceiptIdempotencyColumns() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase24_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE message_send_outbox(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      tenant_id BIGINT NOT NULL,
                      task_id BIGINT NOT NULL,
                      message_id VARCHAR(64) NOT NULL,
                      channel_id BIGINT NOT NULL,
                      state VARCHAR(16) NOT NULL,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE delivery_reports(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      message_id VARCHAR(64) NOT NULL,
                      channel_id BIGINT,
                      upstream_msg_id VARCHAR(128),
                      report_status VARCHAR(16) NOT NULL,
                      error_code VARCHAR(64),
                      raw_payload TEXT,
                      report_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE billing_records(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      task_ref_id BIGINT NOT NULL,
                      billing_status VARCHAR(16) NOT NULL
                    )
                    """);

            String migration = new ClassPathResource("db/migration/V3300__http_upstream_delivery_closure.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            connection.createStatement().execute(migration);

            assertThat(hasColumn(connection, "message_send_outbox", "claim_token")).isTrue();
            assertThat(hasColumn(connection, "message_send_outbox", "provider_message_id")).isTrue();
            assertThat(hasColumn(connection, "delivery_reports", "receipt_digest")).isTrue();
            assertThat(hasIndex(connection, "uk_delivery_reports_receipt_digest")).isTrue();
        }
    }

    private static boolean hasColumn(java.sql.Connection connection, String table, String column) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    private static boolean hasIndex(java.sql.Connection connection, String index) throws Exception {
        for (String table : java.util.List.of("message_send_outbox", "delivery_reports", "billing_records")) {
            try (var indexes = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
                while (indexes.next()) {
                    if (index.equals(indexes.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
