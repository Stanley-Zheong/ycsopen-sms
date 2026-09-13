package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationSettlementInvoicesMigrationTest {
    @Test
    void phase38MigrationExtendsFinanceTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase38_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            createBaseTables(connection);
            String migration = new ClassPathResource("db/migration/V4700__reconciliation_settlement_invoices.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replaceAll("\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasColumn(connection, "statements", "statement_no")).isTrue();
            assertThat(hasColumn(connection, "statements", "tenant_confirmed_at")).isTrue();
            assertThat(hasColumn(connection, "statements", "finance_confirmed_at")).isTrue();
            assertThat(hasTable(connection, "statement_differences")).isTrue();
            assertThat(hasTable(connection, "settlement_records")).isTrue();
            assertThat(hasColumn(connection, "invoices", "statement_id")).isTrue();
            assertThat(hasColumn(connection, "invoices", "request_evidence")).isTrue();
        }
    }

    private static void createBaseTables(Connection connection) throws Exception {
        connection.createStatement().execute("""
                CREATE TABLE statements (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    tenant_id BIGINT NOT NULL,
                    period_start DATE NOT NULL,
                    period_end DATE NOT NULL,
                    send_count BIGINT NOT NULL DEFAULT 0,
                    success_count BIGINT NOT NULL DEFAULT 0,
                    billed_count BIGINT NOT NULL DEFAULT 0,
                    amount_due BIGINT NOT NULL DEFAULT 0,
                    reconcile_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    dispute_note VARCHAR(500),
                    settlement_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SETTLED',
                    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    confirmed_at TIMESTAMP
                )
                """);
        connection.createStatement().execute("""
                CREATE TABLE invoices (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    tenant_id BIGINT NOT NULL,
                    amount BIGINT NOT NULL,
                    invoice_type VARCHAR(32),
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    invoice_no VARCHAR(64),
                    issued_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static boolean hasTable(Connection connection, String table) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}
