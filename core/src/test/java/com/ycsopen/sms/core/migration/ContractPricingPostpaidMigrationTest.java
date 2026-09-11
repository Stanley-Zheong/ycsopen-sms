package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ContractPricingPostpaidMigrationTest {
    @Test
    void phase37MigrationCreatesContractPriceBookAndPostpaidUsageTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase37_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            String migration = new ClassPathResource("db/migration/V4600__contract_pricing_postpaid.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace(" JSON", " VARCHAR(1000)")
                    .replaceAll("\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasTable(connection, "tenant_price_books")).isTrue();
            assertThat(hasTable(connection, "tenant_contracts")).isTrue();
            assertThat(hasTable(connection, "postpaid_usage_ledger")).isTrue();
            assertThat(hasColumn(connection, "tenant_contracts", "billing_mode")).isTrue();
            assertThat(hasColumn(connection, "tenant_contracts", "price_book_version")).isTrue();
            assertThat(hasColumn(connection, "tenant_contracts", "credit_limit_mil")).isTrue();
            assertThat(hasColumn(connection, "tenant_contracts", "billing_period")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM tenant_price_books WHERE price_book_version='SMS_STANDARD_V1'"))
                    .isEqualTo(1);
        }
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

    private static int count(Connection connection, String sql) throws Exception {
        try (var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
