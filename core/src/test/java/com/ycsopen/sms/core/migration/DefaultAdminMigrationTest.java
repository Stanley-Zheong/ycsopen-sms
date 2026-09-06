package com.ycsopen.sms.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DefaultAdminMigrationTest {

    @Test
    void createsActivePlatformAdministratorWithDocumentedPassword() throws Exception {
        try (var connection = database("fresh")) {
            executeMigration(connection);

            try (var result = connection.createStatement().executeQuery(
                    "SELECT password_hash, user_type, status FROM users WHERE username = 'admin'")) {
                assertThat(result.next()).isTrue();
                assertThat(new BCryptPasswordEncoder().matches("Admin@123456", result.getString("password_hash")))
                        .isTrue();
                assertThat(result.getString("user_type")).isEqualTo("ADMIN");
                assertThat(result.getString("status")).isEqualTo("ACTIVE");
            }
        }
    }

    @Test
    void preservesAnExistingAdministratorDuringUpgrade() throws Exception {
        try (var connection = database("upgrade")) {
            connection.createStatement().executeUpdate("""
                    INSERT INTO users (username, password_hash, real_name, user_type, status,
                                       failed_login_count, created_by)
                    VALUES ('admin', 'operator-managed-hash', '现有管理员', 'ADMIN', 'LOCKED', 4, 'manual-setup')
                    """);

            executeMigration(connection);

            try (var result = connection.createStatement().executeQuery(
                    "SELECT password_hash, status, failed_login_count, created_by FROM users WHERE username = 'admin'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("password_hash")).isEqualTo("operator-managed-hash");
                assertThat(result.getString("status")).isEqualTo("LOCKED");
                assertThat(result.getInt("failed_login_count")).isEqualTo(4);
                assertThat(result.getString("created_by")).isEqualTo("manual-setup");
            }
        }
    }

    private static java.sql.Connection database(String name) throws Exception {
        var connection = DriverManager.getConnection("jdbc:h2:mem:default_admin_" + name + ";MODE=MySQL");
        connection.createStatement().execute("""
                CREATE TABLE users (
                    username VARCHAR(50) PRIMARY KEY,
                    password_hash VARCHAR(100) NOT NULL,
                    real_name VARCHAR(50),
                    user_type VARCHAR(20) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    failed_login_count INT NOT NULL,
                    created_by VARCHAR(50)
                )
                """);
        return connection;
    }

    private static void executeMigration(java.sql.Connection connection) throws Exception {
        var migration = new ClassPathResource("db/devmigration/R__create_default_admin.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        connection.createStatement().execute(migration);
    }
}
