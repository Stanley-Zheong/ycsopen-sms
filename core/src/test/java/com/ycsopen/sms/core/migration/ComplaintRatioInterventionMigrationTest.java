package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintRatioInterventionMigrationTest {
    @Test
    void migrationAddsRatioQualitySourceAndPermissions() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase45-migration-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                CREATE TABLE complaint_ratio_stats(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  stat_month CHAR(7) NOT NULL,
                  dimension_type VARCHAR(16) NOT NULL,
                  dimension_id BIGINT NOT NULL,
                  send_count BIGINT NOT NULL,
                  complaint_count BIGINT NOT NULL,
                  ratio DECIMAL(12,6) NOT NULL,
                  over_threshold BOOLEAN NOT NULL,
                  threshold_config_version VARCHAR(32),
                  calculated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
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
        jdbc.update("""
                INSERT INTO complaint_ratio_stats(stat_month, dimension_type, dimension_id, send_count, complaint_count,
                                                  ratio, over_threshold, calculated_at)
                VALUES ('2026-08', 'CHANNEL', 11, 0, 1, 0, false, CURRENT_TIMESTAMP)
                """);

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5400__complaint_ratio_intervention.sql"))
                .execute(database);

        assertThat(jdbc.queryForObject("""
                SELECT data_quality FROM complaint_ratio_stats WHERE stat_month='2026-08' AND dimension_id=11
                """, String.class)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("""
                SELECT source_registry FROM complaint_ratio_stats WHERE stat_month='2026-08' AND dimension_id=11
                """, String.class)).isEqualTo("complaint_ratio_stats:message_tasks:complaints");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM permissions WHERE permission_code IN
                    ('complaint-ratio:menu','complaint-ratio:read','complaint-ratio:intervene')
                """, Integer.class)).isEqualTo(3);
    }
}
