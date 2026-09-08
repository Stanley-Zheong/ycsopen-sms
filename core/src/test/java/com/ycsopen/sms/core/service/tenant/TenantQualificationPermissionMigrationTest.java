package com.ycsopen.sms.core.service.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantQualificationPermissionMigrationTest {
    @Test
    void addsSafeInspectionFactsAndExactlyTheSixDocumentedPermissions() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tenants(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE permissions(id BIGINT AUTO_INCREMENT PRIMARY KEY, permission_code VARCHAR(100) UNIQUE, permission_name VARCHAR(100), resource_type ENUM('MENU','BUTTON','API','DATA'), resource_path VARCHAR(200), http_method VARCHAR(10), parent_id BIGINT, sort_order INT, status ENUM('ACTIVE','DISABLED'))");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1701__tenant_qualification_permissions.sql"));
        }
        assertThat(jdbc.queryForList("SELECT permission_code FROM permissions ORDER BY sort_order", String.class))
                .containsExactly("tenant:menu", "tenant:read", "tenant:qualification:review",
                        "tenant:update", "tenant:status:update", "tenant:evidence:read");
        List<String> columns = jdbc.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME = 'TENANTS' AND COLUMN_NAME LIKE 'INSPECTION_%'
                 ORDER BY COLUMN_NAME
                """, String.class);
        assertThat(columns).containsExactly("INSPECTION_COMPANY_NAME", "INSPECTION_COMPLETED_AT",
                "INSPECTION_CONFIDENCE", "INSPECTION_CREDIT_CODE",
                "INSPECTION_PROVIDER_REQUEST_ID", "INSPECTION_STATUS");
    }
}
