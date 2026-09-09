package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class SecureHttpMessageAcceptanceMigrationTest {

    @Test
    void phase23MigrationAddsRequestDigestOutboxAndSubmitIndex() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase23_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE message_submits(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      submit_id VARCHAR(64) NOT NULL,
                      tenant_id BIGINT NOT NULL,
                      source_protocol VARCHAR(16) NOT NULL,
                      product_type VARCHAR(32) NOT NULL,
                      signature_id BIGINT,
                      template_id BIGINT,
                      status VARCHAR(16) NOT NULL,
                      reject_reason VARCHAR(255),
                      UNIQUE KEY uk_tenant_submit (tenant_id, submit_id)
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE message_tasks(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      message_id VARCHAR(64) NOT NULL,
                      submit_id BIGINT,
                      tenant_id BIGINT NOT NULL,
                      UNIQUE KEY uk_message_id (message_id)
                    )
                    """);

            String migration = new ClassPathResource("db/migration/V3200__secure_http_message_acceptance.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            connection.createStatement().execute(migration);

            try (var columns = connection.getMetaData().getColumns(null, null,
                    "message_submits", "request_digest")) {
                assertThat(columns.next()).isTrue();
            }
            try (var tables = connection.getMetaData().getTables(null, null,
                    "message_send_outbox", null)) {
                assertThat(tables.next()).isTrue();
            }
            try (var indexes = connection.getMetaData().getIndexInfo(null, null,
                    "message_tasks", false, false)) {
                boolean found = false;
                while (indexes.next()) {
                    found |= "idx_message_tasks_submit_id".equals(indexes.getString("INDEX_NAME"));
                }
                assertThat(found).isTrue();
            }
        }
    }
}
