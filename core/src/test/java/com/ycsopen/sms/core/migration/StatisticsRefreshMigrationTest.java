package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsRefreshMigrationTest {
    @Test
    void v6700CreatesEpochRefreshStateCheckpointsAndBackfillsQueuedClaimLease() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:issue119_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE message_submits(
                      id BIGINT PRIMARY KEY,
                      status VARCHAR(16) NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            connection.createStatement().execute("INSERT INTO message_submits(id,status) VALUES (7,'QUEUED'),(8,'ACCEPTED')");
            connection.createStatement().execute("""
                    CREATE TABLE message_tasks(
                      id BIGINT PRIMARY KEY,
                      created_at TIMESTAMP NOT NULL,
                      updated_at TIMESTAMP NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE delivery_reports(
                      id BIGINT PRIMARY KEY,
                      message_id VARCHAR(64) NOT NULL,
                      report_time TIMESTAMP NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE billing_records(
                      id BIGINT PRIMARY KEY,
                      task_ref_id BIGINT NOT NULL,
                      created_at TIMESTAMP NOT NULL
                    )
                    """);

            String migration = new ClassPathResource(
                    "db/migration/V6700__statistics_refresh_and_submit_claim_recovery.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME(6)", "TIMESTAMP")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP(6)", "")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("(?s)\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\\s+COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(count(connection, "SELECT COUNT(*) FROM statistics_refresh_state "
                    + "WHERE pipeline_code='STATISTICS_AGGREGATION' "
                    + "AND scanned_through=TIMESTAMP '1970-01-01 00:00:00'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM statistics_refresh_checkpoints")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM message_submit_claim_leases WHERE submission_id=7"))
                    .isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM message_submit_claim_leases WHERE submission_id=8"))
                    .isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME='message_submits' AND COLUMN_NAME='updated_at'"))
                    .isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                    + "WHERE INDEX_NAME IN ('idx_message_submits_statistics_refresh',"
                    + "'idx_message_submits_statistics_bucket','idx_message_tasks_statistics_changed',"
                    + "'idx_message_tasks_statistics_bucket','idx_delivery_reports_statistics_changed',"
                    + "'idx_billing_records_statistics_changed')"))
                    .isEqualTo(6);
        }
    }

    private static int count(java.sql.Connection connection, String sql) throws Exception {
        try (var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
