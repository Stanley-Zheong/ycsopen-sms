package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRechargeOperationsMigrationTest {
    @Test
    void phase36MigrationCreatesRechargeReviewTableWithProtectedReferenceFields() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase36_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            String migration = new ClassPathResource("db/migration/V4500__tenant_recharge_operations.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replaceAll("\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']*'", ")");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasTable(connection, "tenant_recharge_records")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "tenant_id")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "amount_mil")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "recharge_method")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "transaction_ref_hash")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "transaction_ref_mask")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "evidence_text")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "reviewer_actor")).isTrue();
            assertThat(hasColumn(connection, "tenant_recharge_records", "review_reason")).isTrue();
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
}
