package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintCaseManagementMigrationTest {

    @Test
    void migrationCreatesComplaintCaseAndRemediationEvidenceColumns() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase41-migration-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
                .addScript("db/migration/V5000__complaint_case_management.sql")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(database);

        jdbc.update("""
                INSERT INTO complaints(source, summary, status, attribution_quality, content_type, complained_mobile,
                                       requirement, created_by)
                VALUES ('REGULATOR', '监管投诉', 'PENDING', 'UNKNOWN', 'MARKETING', '13800138000', '24小时反馈', 'operator')
                """);
        Long id = jdbc.queryForObject("SELECT id FROM complaints WHERE summary='监管投诉'", Long.class);
        jdbc.update("""
                INSERT INTO disposal_records(complaint_id, disposal_type, target_ref, status, authorized_review_id,
                                             original_complaint_id, disposed_by)
                VALUES (?, 'SUSPEND_CHANNEL', 'channel:11', 'FAILED', 'review-1', ?, 'operator')
                """, id, id);

        assertThat(jdbc.queryForObject("SELECT attribution_quality FROM complaints WHERE id=?", String.class, id))
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT status FROM disposal_records WHERE complaint_id=?", String.class, id))
                .isEqualTo("FAILED");
    }

    @Test
    void migrationExtendsExistingComplaintTablesAndAllowsCarrierSource() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase41-existing-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                CREATE TABLE complaints (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    source ENUM('REGULATOR','OPERATOR','USER_REPORT') NOT NULL,
                    tenant_id BIGINT NULL,
                    signature_id BIGINT NULL,
                    template_id BIGINT NULL,
                    message_id VARCHAR(64) NULL,
                    channel_id BIGINT NULL,
                    summary VARCHAR(500),
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    handling_note VARCHAR(500),
                    corrective_action VARCHAR(500),
                    handled_by VARCHAR(64),
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE disposal_records (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    complaint_id BIGINT NOT NULL,
                    disposal_type VARCHAR(64) NOT NULL,
                    target_ref VARCHAR(64) NOT NULL,
                    disposed_by VARCHAR(64),
                    disposed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    resumed_at DATETIME NULL,
                    resume_condition VARCHAR(255)
                )
                """);

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5000__complaint_case_management.sql"))
                .execute(database);

        jdbc.update("""
                INSERT INTO complaints(source, summary, status, attribution_quality, content_type, complained_mobile,
                                       requirement, created_by)
                VALUES ('CARRIER', '运营商投诉', 'PENDING', 'UNKNOWN', 'MARKETING', '13800138000', '反馈运营商', 'operator')
                """);

        assertThat(jdbc.queryForObject("SELECT source FROM complaints WHERE summary='运营商投诉'", String.class))
                .isEqualTo("CARRIER");
    }
}
