package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.config.RuntimeDatabaseGrantCallback;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.service.audit.SecurityEventService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real MySQL proof for Phase 06 schema, append-only triggers, and deduplication. */
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase06AuditMySqlIntegrationTest {

    @Test
    void migratedAuditIsAppendProtectedUnderTheRuntimeAccount() throws Exception {
        try (Phase01ServiceSession mysql = Phase01ServiceHarness.startMySql()) {
            String migrationUser = "phase06_migrator";
            String migrationPassword = "Phase06MigrationOnly!";
            String runtimeUser = "phase06_runtime";
            String runtimePassword = "Phase06RuntimeOnly!";
            rootSql(mysql, """
                    CREATE USER '%s'@'%%' IDENTIFIED BY '%s';
                    CREATE USER '%s'@'%%' IDENTIFIED BY '%s';
                    GRANT ALL PRIVILEGES ON phase01.* TO '%s'@'%%' WITH GRANT OPTION;
                    FLUSH PRIVILEGES;
                    """.formatted(migrationUser, migrationPassword, runtimeUser,
                    runtimePassword, migrationUser));

            DriverManagerDataSource migrationDataSource = new DriverManagerDataSource(
                    mysql.jdbcUrl(), migrationUser, migrationPassword);
            rootSql(mysql, "SET GLOBAL log_bin_trust_function_creators=OFF");
            Flyway blockedFlyway = Flyway.configure().dataSource(migrationDataSource)
                    .callbacks(new RuntimeDatabaseGrantCallback(true, runtimeUser, "%"))
                    .placeholderReplacement(false).load();
            assertThatThrownBy(blockedFlyway::migrate)
                    .hasStackTraceContaining("log_bin_trust_function_creators=ON")
                    .hasStackTraceContaining("V1500");

            rootSql(mysql, "SET GLOBAL log_bin_trust_function_creators=ON");
            Flyway flyway = Flyway.configure().dataSource(migrationDataSource)
                    .callbacks(new RuntimeDatabaseGrantCallback(true, runtimeUser, "%"))
                    .placeholderReplacement(false).load();
            flyway.migrate();
            DriverManagerDataSource runtimeDataSource = new DriverManagerDataSource(
                    mysql.jdbcUrl(), runtimeUser, runtimePassword);
            JdbcTemplate jdbc = new JdbcTemplate(runtimeDataSource);
            jdbc.update("""
                    INSERT INTO users(username, password_hash, user_type, status)
                    VALUES ('phase06-admin', '$2a$04$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuu', 'ADMIN', 'ACTIVE')
                    """);
            long userId = jdbc.queryForObject(
                    "SELECT id FROM users WHERE username='phase06-admin'", Long.class);

            OperationAuditService audits = new OperationAuditService(jdbc);
            long auditId = audits.start(new OperationAuditService.AuditCommand(
                    userId, "PUT /api/v1/console/platform-roles/{roleId}/permissions",
                    "CONSOLE_HTTP", "roleId=12", "PUT",
                    "/api/v1/console/platform-roles/{roleId}/permissions",
                    "{\"fields\":{\"password\":\"[redacted]\"}}", "SUCCESS", 200,
                    "192.0.2.9", "0123456789abcdef0123456789abcdef", 17));

            assertThat(jdbc.queryForObject(
                    "SELECT result_code FROM privileged_operation_audits WHERE id=?",
                    String.class, auditId)).isEqualTo("STARTED");
            audits.complete(auditId, "SUCCESS", 200, 17);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM privileged_operation_audits WHERE id=? AND trace_id=?",
                    Integer.class, auditId, "0123456789abcdef0123456789abcdef")).isOne();
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE privileged_operation_audits SET result_code='DENIED' WHERE id=?", auditId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update(
                    "DELETE FROM privileged_operation_audits WHERE id=?", auditId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE TABLE privileged_operation_audits"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("DROP TRIGGER trg_privileged_audit_no_update"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("DROP TABLE privileged_operation_audits"))
                    .isInstanceOf(DataAccessException.class);
            String grants = String.join("\n", jdbc.queryForList("SHOW GRANTS", String.class));
            assertThat(grants).contains("SELECT", "INSERT", "privileged_operation_audits")
                    .doesNotContain("ALL PRIVILEGES ON `phase01`.*");

            SecurityEventService events = new SecurityEventService(jdbc);
            long first = events.recordBulkExport(userId, null, 42L, 1000,
                    SecurityEventService.SecurityEventResult.SUCCESS,
                    "192.0.2.9", null).orElseThrow();
            long retry = events.recordBulkExport(userId, null, 42L, 1000,
                    SecurityEventService.SecurityEventResult.SUCCESS,
                    "192.0.2.9", null).orElseThrow();
            assertThat(retry).isEqualTo(first);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE permission_code LIKE 'audit:%' OR permission_code LIKE 'privileged:data:%'",
                    Integer.class)).isEqualTo(7);

            long interruptedAuditId = audits.start(new OperationAuditService.AuditCommand(
                    userId, "POST /api/v1/console/example", "CONSOLE_HTTP", null,
                    "POST", "/api/v1/console/example", "{\"fields\":{}}",
                    "SUCCESS", 200, "192.0.2.9", null, 0));
            rootSql(mysql, "DROP PROCEDURE finalize_privileged_operation_audit");
            assertThatThrownBy(() -> audits.complete(interruptedAuditId, "SUCCESS", 200, 3))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT result_code FROM privileged_operation_audits WHERE id=?",
                    String.class, interruptedAuditId)).isEqualTo("STARTED");
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1501");
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        }
    }

    private static void rootSql(Phase01ServiceSession mysql, String sql)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                System.getenv().getOrDefault("PHASE01_DOCKER_BIN", "docker"),
                "exec", "--env", "MYSQL_PWD", mysql.containerName(),
                "mysql", "-uroot", "-Dphase01", "-e", sql);
        builder.environment().put("MYSQL_PWD", mysql.rootPassword());
        Process process = builder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("root SQL setup timed out");
        }
        if (process.exitValue() != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("root SQL setup failed: " + stderr);
        }
    }
}
