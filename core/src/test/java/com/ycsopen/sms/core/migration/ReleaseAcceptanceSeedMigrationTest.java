package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseAcceptanceSeedMigrationTest {

    private static final ClassPathResource ADMIN_SEED =
            new ClassPathResource("db/devmigration/R__create_default_admin.sql");
    private static final ClassPathResource RELEASE_SEED =
            new ClassPathResource("db/releasemigration/R__create_release_acceptance_seed.sql");

    @Test
    void seedsAuthenticatedReleaseFixturesOnAFreshDatabase() throws Exception {
        assertThat(RELEASE_SEED.exists())
                .as("removing the release seed leaves Docker acceptance without API fixtures")
                .isTrue();
        try (Connection connection = database("fresh")) {
            executeSeeds(connection);

            assertThat(count(connection, "SELECT COUNT(*) FROM channels WHERE channel_name='DEV-CMPP-PRIMARY'"))
                    .isOne();
            assertThat(count(connection, "SELECT COUNT(*) FROM templates WHERE template_code='DEV-VERIFY-CODE'"))
                    .isOne();
            assertThat(count(connection, "SELECT COUNT(*) FROM number_prefix_mappings WHERE prefix='1380013'"))
                    .isOne();
            assertThat(count(connection, """
                    SELECT COUNT(*) FROM role_permissions rp
                    JOIN user_roles ur ON ur.role_id=rp.role_id
                    JOIN users u ON u.id=ur.user_id
                    WHERE u.username='admin'
                    """))
                    .isEqualTo(count(connection, "SELECT COUNT(*) FROM permissions WHERE status='ACTIVE'"));
        }
    }

    @Test
    void rerunningTheReleaseSeedPreservesExistingRowsWithoutDuplicates() throws Exception {
        assertThat(RELEASE_SEED.exists()).isTrue();
        try (Connection connection = database("upgrade")) {
            executeSeeds(connection);
            connection.createStatement().executeUpdate("""
                    UPDATE channels SET host='operator-managed.invalid'
                    WHERE channel_name='DEV-CMPP-PRIMARY'
                    """);

            ScriptUtils.executeSqlScript(connection, RELEASE_SEED);

            assertThat(count(connection, "SELECT COUNT(*) FROM channels WHERE channel_name='DEV-CMPP-PRIMARY'"))
                    .isOne();
            assertThat(count(connection, "SELECT COUNT(*) FROM templates WHERE template_code='DEV-VERIFY-CODE'"))
                    .isOne();
            assertThat(count(connection, "SELECT COUNT(*) FROM number_prefix_mappings WHERE prefix='1380013'"))
                    .isOne();
            try (var row = connection.createStatement().executeQuery(
                    "SELECT host FROM channels WHERE channel_name='DEV-CMPP-PRIMARY'")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("operator-managed.invalid");
            }
        }
    }

    private static Connection database(String name) throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:release_seed_" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        String[] schema = {
                "CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) UNIQUE, password_hash VARCHAR(100), real_name VARCHAR(50), user_type VARCHAR(20), status VARCHAR(20), failed_login_count INT, created_by VARCHAR(50))",
                "CREATE TABLE roles(id BIGINT AUTO_INCREMENT PRIMARY KEY, role_code VARCHAR(50) UNIQUE, role_name VARCHAR(100), description VARCHAR(255), role_type VARCHAR(20), tenant_id BIGINT, status VARCHAR(20))",
                "CREATE TABLE permissions(id BIGINT AUTO_INCREMENT PRIMARY KEY, permission_code VARCHAR(100) UNIQUE, status VARCHAR(20))",
                "CREATE TABLE user_roles(user_id BIGINT, role_id BIGINT, granted_by VARCHAR(50), PRIMARY KEY(user_id, role_id))",
                "CREATE TABLE role_permissions(role_id BIGINT, permission_id BIGINT, PRIMARY KEY(role_id, permission_id))",
                "CREATE TABLE tenants(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_no VARCHAR(32) UNIQUE, short_name VARCHAR(20), full_name VARCHAR(100), unified_social_credit_code VARCHAR(18), verification_status VARCHAR(32), lifecycle_status VARCHAR(32), created_by VARCHAR(64))",
                "CREATE TABLE signatures(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, biz_type VARCHAR(20), sign_code VARCHAR(32), sign_content VARCHAR(64), sign_type VARCHAR(20), usage_type VARCHAR(20), risk_level VARCHAR(20), audit_status VARCHAR(20), audit_time TIMESTAMP, audit_comment VARCHAR(500), UNIQUE(tenant_id, sign_code))",
                "CREATE TABLE templates(id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, biz_type VARCHAR(20), template_code VARCHAR(32), template_name VARCHAR(50), template_type VARCHAR(20), content VARCHAR(500), signature_id BIGINT, param_check_rule VARCHAR(255), description VARCHAR(255), audit_status VARCHAR(32), audit_time TIMESTAMP, audit_comment VARCHAR(500), is_system_template BOOLEAN, UNIQUE(tenant_id, template_code))",
                "CREATE TABLE channels(id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_name VARCHAR(64), protocol VARCHAR(20), operator VARCHAR(20), host VARCHAR(128), port INT, sp_id VARCHAR(32), service_id VARCHAR(16), src_id VARCHAR(32), max_connections INT, window_size INT, price DECIMAL(10,4), priority INT, active_window VARCHAR(64), extra_config VARCHAR(500), status VARCHAR(20))",
                "CREATE TABLE number_prefix_versions(id BIGINT AUTO_INCREMENT PRIMARY KEY, version_no VARCHAR(32) UNIQUE, update_type VARCHAR(20), status VARCHAR(20), source_name VARCHAR(64), total_rows INT, conflict_count INT, actor VARCHAR(64), activated_at TIMESTAMP)",
                "CREATE TABLE number_prefix_mappings(id BIGINT AUTO_INCREMENT PRIMARY KEY, version_id BIGINT, prefix VARCHAR(7), carrier VARCHAR(20), province VARCHAR(32), city VARCHAR(32), source_name VARCHAR(64), status VARCHAR(20), UNIQUE(version_id, prefix))"
        };
        for (String statement : schema) {
            connection.createStatement().execute(statement);
        }
        connection.createStatement().executeUpdate(
                "INSERT INTO permissions(permission_code,status) VALUES "
                        + "('number-attribution:read','ACTIVE'),('channel:read','ACTIVE'),('template:read','ACTIVE')");
        return connection;
    }

    private static void executeSeeds(Connection connection) {
        ScriptUtils.executeSqlScript(connection, ADMIN_SEED);
        ScriptUtils.executeSqlScript(connection, RELEASE_SEED);
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
