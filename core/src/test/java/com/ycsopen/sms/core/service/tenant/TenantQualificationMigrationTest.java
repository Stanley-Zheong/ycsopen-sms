package com.ycsopen.sms.core.service.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Executes the portable DDL portion; immutable MySQL triggers require the physical MySQL gate. */
class TenantQualificationMigrationTest {
    @Test void expandsExistingTenantSchemaAndAcceptsTheEncryptedChallengeWriterContract() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tenants (id BIGINT PRIMARY KEY, verification_status ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'UNVERIFIED')");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        String migration;
        try (var input = getClass().getResourceAsStream("/db/migration/V1700__tenant_qualification_status.sql")) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8).split("-- MySQL immutable event guards")[0];
        }
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)));
        }
        jdbc.update("INSERT INTO tenants (id,verification_status,trademark_use,qualification_revision) VALUES (1,'SUPPLEMENT_REQUIRED',false,0)");
        jdbc.update("INSERT INTO tenant_contact_verification_challenges (challenge_id,phone_encrypted,code_hash,expires_at,request_ip) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), new byte[]{1,2,3}, "$2a$10$opaque-test-hash", java.sql.Timestamp.from(java.time.Instant.now()), "127.0.0.1");
        assertThat(jdbc.queryForObject("select verification_status from tenants", String.class)).isEqualTo("SUPPLEMENT_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from tenant_contact_verification_challenges", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE tenant_contact_verification_challenges SET attempt_count=6")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO tenant_qualification_events (tenant_id,action,actor,changed_fields) VALUES (1,'SUBMITTED','public-registration','qualification')");
        assertThat(jdbc.queryForObject("select count(*) from tenant_qualification_events", Integer.class)).isEqualTo(1);
    }
}
