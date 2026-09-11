package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalDashboardsMigrationTest {
    @Test
    void createsOperationalDashboardConfigurationAndPermissions() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase44-migration-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE permissions(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      permission_code VARCHAR(100) UNIQUE NOT NULL,
                      permission_name VARCHAR(100) NOT NULL,
                      resource_type VARCHAR(20) NOT NULL,
                      resource_path VARCHAR(255) NULL,
                      http_method VARCHAR(10) NULL,
                      parent_id BIGINT NULL,
                      sort_order INT NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            String migration = new String(new ClassPathResource("db/migration/V5300__operational_dashboards.sql")
                    .getInputStream().readAllBytes());

            connection.createStatement().execute(migration);

            assertThat(hasTable(connection, "operational_dashboard_configs")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM operational_dashboard_configs")).isGreaterThanOrEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM permissions WHERE permission_code='operational-dashboard:read'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM permissions WHERE permission_code='operational-dashboard:write'")).isEqualTo(1);
        }
    }

    private static boolean hasTable(Connection connection, String table) throws Exception {
        try (var rs = connection.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var rs = connection.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
