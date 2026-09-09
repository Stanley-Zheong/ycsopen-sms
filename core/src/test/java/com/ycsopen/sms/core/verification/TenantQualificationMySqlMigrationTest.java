package com.ycsopen.sms.core.verification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Physical migration and trigger proof on the repository's isolated, pinned MySQL fixture. */
@EnabledIfSystemProperty(named = "phase08.integration.enabled", matches = "true")
class TenantQualificationMySqlMigrationTest {
    @Test void migratesTheExistingSchemaAndRejectsEventMutation() {
        try (var mysql = Phase03ServiceHarness.startMySql()) {
            var source = new DriverManagerDataSource("jdbc:mysql://" + mysql.host() + ":" + mysql.port()
                    + "/phase01?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", mysql.username(), mysql.password());
            Flyway.configure().dataSource(source).placeholderReplacement(false).locations("classpath:db/migration").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO tenants (tenant_no,short_name,full_name,unified_social_credit_code,verification_status) VALUES ('T-test','测试','测试机构','91350211M000100Y46','SUPPLEMENT_REQUIRED')");
            Long tenantId = jdbc.queryForObject("SELECT id FROM tenants WHERE tenant_no='T-test'", Long.class);
            jdbc.update("INSERT INTO tenant_qualification_events (tenant_id,action,actor,changed_fields) VALUES (?,'SUBMITTED','public-registration','qualification')", tenantId);
            assertThatThrownBy(() -> jdbc.update("UPDATE tenant_qualification_events SET action='REWRITTEN' WHERE tenant_id=?", tenantId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM tenant_qualification_events WHERE tenant_id=?", tenantId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant_qualification_events WHERE tenant_id=?", Integer.class, tenantId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT qualification_revision FROM tenants WHERE id=?", Long.class, tenantId)).isZero();
        }
    }
}
