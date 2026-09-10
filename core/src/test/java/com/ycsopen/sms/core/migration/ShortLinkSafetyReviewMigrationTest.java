package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkSafetyReviewMigrationTest {
    @Test
    void phase48MigrationExtendsExistingShortLinkTablesAndAddsDomainClickPermissions() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:phase48_migration;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            connection.createStatement().execute("""
                    CREATE TABLE short_links(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      tenant_id BIGINT NOT NULL,
                      target_url VARCHAR(2000) NOT NULL,
                      custom_domain VARCHAR(128),
                      short_code VARCHAR(16) NOT NULL UNIQUE,
                      valid_until DATE NOT NULL,
                      status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                      click_count BIGINT NOT NULL DEFAULT 0,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE short_link_audits(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      short_link_id BIGINT NOT NULL,
                      auto_check_result VARCHAR(4000),
                      risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
                      reviewer VARCHAR(64),
                      review_comment VARCHAR(255),
                      reviewed_at TIMESTAMP,
                      last_recheck_at TIMESTAMP
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE permissions(
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      permission_code VARCHAR(100) NOT NULL UNIQUE,
                      permission_name VARCHAR(100) NOT NULL,
                      resource_type VARCHAR(50) NOT NULL,
                      resource_path VARCHAR(255),
                      http_method VARCHAR(10),
                      parent_id BIGINT,
                      sort_order INT,
                      status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    )
                    """);
            String migration = new ClassPathResource("db/migration/V5700__shortlink_safety_review.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("BIGINT UNSIGNED", "BIGINT")
                    .replace("DATETIME", "TIMESTAMP")
                    .replace(" JSON", " VARCHAR(4000)")
                    .replace(" ON UPDATE CURRENT_TIMESTAMP", "")
                    .replace("PRIMARY KEY AUTO_INCREMENT", "AUTO_INCREMENT PRIMARY KEY")
                    .replaceAll("ENUM\\([^)]*\\)", "VARCHAR(32)")
                    .replaceAll("ALTER TABLE short_links MODIFY[^;]+;", "")
                    .replaceAll("ALTER TABLE short_links ADD KEY[^;]+;", "")
                    .replaceAll("(?m)^\\s+UNIQUE KEY [^\\n]+\\n", "")
                    .replaceAll("(?m)^\\s+KEY [^\\n]+\\n", "")
                    .replaceAll(",\\s*\\)", ")")
                    .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='[^']+'", "");
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) {
                    connection.createStatement().execute(sql.trim());
                }
            }

            assertThat(hasColumn(connection, "short_links", "automated_result_json")).isTrue();
            assertThat(hasColumn(connection, "short_link_audits", "audit_action")).isTrue();
            assertThat(hasTable(connection, "short_link_domains")).isTrue();
            assertThat(hasTable(connection, "short_link_click_events")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM short_link_domains")).isEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM permissions WHERE permission_code LIKE 'shortlink%'"))
                    .isEqualTo(6);
        }
    }

    private static boolean hasTable(Connection connection, String table) throws Exception {
        try (var rows = connection.getMetaData().getTables(null, null, table, null)) {
            return rows.next();
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (var rows = connection.getMetaData().getColumns(null, null, table, column)) {
            return rows.next();
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
