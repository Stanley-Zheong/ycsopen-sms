package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchTaskRecoveryMigrationTest {
    @Test
    void phase25MigrationAddsRecoveryEventAndTestTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase25_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            String migration = new ClassPathResource("db/migration/V3400__dispatch_task_migration_recovery.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("TINYINT(1)", "BOOLEAN")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase25 dispatch task migration and retry evidence'", "")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Phase25 small-traffic channel recovery evidence'", "")
                    .replace("ENUM('MIGRATED','RETRY_CREATED','NO_BACKUP')", "VARCHAR(32)");
            for (String statement : migration.split(";")) {
                if (!statement.isBlank()) {
                    connection.createStatement().execute(statement);
                }
            }

            assertThat(hasTable(connection, "dispatch_recovery_events")).isTrue();
            assertThat(hasTable(connection, "channel_recovery_tests")).isTrue();
        }
    }

    private static boolean hasTable(java.sql.Connection connection, String table) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }
}
