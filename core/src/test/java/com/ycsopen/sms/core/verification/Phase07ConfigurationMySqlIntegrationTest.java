package com.ycsopen.sms.core.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.config.RuntimeDatabaseGrantCallback;
import com.ycsopen.sms.core.service.configuration.PlatformConfigurationRegistry;
import com.ycsopen.sms.core.service.configuration.PlatformConfigurationRuntime;
import com.ycsopen.sms.core.service.configuration.PlatformConfigurationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real MySQL proof for Phase 07 migrations, versions, concurrency, reload, and rollback. */
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase07ConfigurationMySqlIntegrationTest {

    @Test
    void typedVersionsRemainAttributableAndRollbackCreatesANewActiveVersion() throws Exception {
        try (Phase01ServiceSession mysql = Phase01ServiceHarness.startMySql()) {
            String migrationUser = "phase07_migrator";
            String migrationPassword = "Phase07MigrationOnly!";
            String runtimeUser = "phase07_runtime";
            String runtimePassword = "Phase07RuntimeOnly!";
            rootSql(mysql, """
                    SET GLOBAL log_bin_trust_function_creators=ON;
                    CREATE USER '%s'@'%%' IDENTIFIED BY '%s';
                    CREATE USER '%s'@'%%' IDENTIFIED BY '%s';
                    GRANT ALL PRIVILEGES ON phase01.* TO '%s'@'%%' WITH GRANT OPTION;
                    FLUSH PRIVILEGES;
                    """.formatted(migrationUser, migrationPassword, runtimeUser,
                    runtimePassword, migrationUser));

            DriverManagerDataSource migrationDataSource = new DriverManagerDataSource(
                    mysql.jdbcUrl(), migrationUser, migrationPassword);
            Flyway flyway = Flyway.configure().dataSource(migrationDataSource)
                    .callbacks(new RuntimeDatabaseGrantCallback(true, runtimeUser, "%"))
                    .placeholderReplacement(false).load();
            flyway.migrate();

            DriverManagerDataSource runtimeDataSource = new DriverManagerDataSource(
                    mysql.jdbcUrl(), runtimeUser, runtimePassword);
            JdbcTemplate jdbc = new JdbcTemplate(runtimeDataSource);
            jdbc.update("""
                    INSERT INTO users(username, password_hash, user_type, status)
                    VALUES ('phase07-admin', '$2a$04$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuu',
                            'ADMIN', 'ACTIVE')
                    """);
            long actor = jdbc.queryForObject(
                    "SELECT id FROM users WHERE username = 'phase07-admin'", Long.class);
            PlatformConfigurationRegistry registry = new PlatformConfigurationRegistry();
            PlatformConfigurationRuntime runtime = new PlatformConfigurationRuntime(registry);
            DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(runtimeDataSource);
            PlatformConfigurationService service = new PlatformConfigurationService(
                    jdbc, new ObjectMapper(), transactionManager,
                    registry, runtime);
            service.initializeRuntime();

            long firstDraft = service.stage(new PlatformConfigurationService.StageCommand(
                    0, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8"),
                    "raise threshold", actor));
            String firstJson = jdbc.queryForObject(
                    "SELECT CAST(values_json AS CHAR) FROM platform_configuration_versions WHERE id = ?",
                    String.class, firstDraft);
            assertThat(firstJson).contains("env:YCS_SMS_EXPORT_SIGNING_KEY").doesNotContain("••••••");
            assertThat(service.view().settings().stream()
                    .filter(setting -> setting.key().equals(
                            PlatformConfigurationRegistry.EXPORT_SIGNING_KEY_REFERENCE))
                    .findFirst().orElseThrow().value())
                    .isEqualTo(PlatformConfigurationRegistry.MASKED_SECRET_REFERENCE);

            service.activate(new PlatformConfigurationService.ActivateCommand(
                    firstDraft, 0, "activate first", actor));
            assertThat(runtime.loginMaxFailures()).isEqualTo(8);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM platform_configuration_versions
                    WHERE id = ? AND status = 'ACTIVE' AND reload_status = 'APPLIED'
                      AND created_by = ? AND activated_by = ?
                    """, Integer.class, firstDraft, actor, actor)).isOne();

            long staleDraft = service.stage(new PlatformConfigurationService.StageCommand(
                    firstDraft, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "10"),
                    "stale candidate", actor));
            assertThatThrownBy(() -> service.activate(new PlatformConfigurationService.ActivateCommand(
                    staleDraft, 0, "wrong base", actor)))
                    .isInstanceOf(PlatformConfigurationService.Failure.class)
                    .extracting(error -> ((PlatformConfigurationService.Failure) error).code())
                    .isEqualTo(PlatformConfigurationService.FailureCode.STALE_VERSION);
            assertThat(runtime.loginMaxFailures()).isEqualTo(8);

            service.activate(new PlatformConfigurationService.ActivateCommand(
                    staleDraft, firstDraft, "activate second", actor));
            String immutableFirstJson = jdbc.queryForObject(
                    "SELECT CAST(values_json AS CHAR) FROM platform_configuration_versions WHERE id = ?",
                    String.class, firstDraft);
            assertThat(immutableFirstJson).isEqualTo(firstJson);

            PlatformConfigurationService.Activation rollback = service.rollback(
                    new PlatformConfigurationService.RollbackCommand(
                            firstDraft, staleDraft, "restore first", actor));
            assertThat(rollback.activeVersion()).isGreaterThan(staleDraft);
            assertThat(runtime.loginMaxFailures()).isEqualTo(8);
            assertThat(jdbc.queryForObject("""
                    SELECT source_version_id FROM platform_configuration_versions WHERE id = ?
                    """, Long.class, rollback.activeVersion())).isEqualTo(firstDraft);

            long recoveryDraft = service.stage(new PlatformConfigurationService.StageCommand(
                    rollback.activeVersion(),
                    Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "12"),
                    "simulate committed pending restart", actor));
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.update("""
                        UPDATE platform_configuration_versions SET status = 'SUPERSEDED'
                        WHERE id = ? AND status = 'ACTIVE'
                        """, rollback.activeVersion());
                jdbc.update("""
                        UPDATE platform_configuration_versions
                        SET status = 'ACTIVE', activated_by = ?, activated_at = CURRENT_TIMESTAMP,
                            activation_reason = 'simulate crash before apply', reload_status = 'PENDING'
                        WHERE id = ? AND status = 'DRAFT'
                        """, actor, recoveryDraft);
                jdbc.update("""
                        UPDATE platform_configuration_state
                        SET active_version_id = ?, lock_version = lock_version + 1,
                            reload_status = 'PENDING', updated_at = CURRENT_TIMESTAMP
                        WHERE id = 1
                        """, recoveryDraft);
            });
            PlatformConfigurationRuntime recoveredRuntime = new PlatformConfigurationRuntime(registry);
            PlatformConfigurationService recoveredService = new PlatformConfigurationService(
                    jdbc, new ObjectMapper(), transactionManager, registry, recoveredRuntime);
            recoveredService.initializeRuntime();
            assertThat(recoveredRuntime.version()).isEqualTo(recoveryDraft);
            assertThat(recoveredRuntime.loginMaxFailures()).isEqualTo(12);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM platform_configuration_versions v
                    JOIN platform_configuration_state s ON s.active_version_id = v.id
                    WHERE v.id = ? AND v.reload_status = 'APPLIED'
                      AND s.reload_status = 'APPLIED'
                    """, Integer.class, recoveryDraft)).isOne();

            recoveredRuntime.apply(recoveredRuntime.prepare(firstDraft,
                    registry.mergeAndValidate(registry.defaults(), Map.of(
                            PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8"))));
            assertThat(recoveredRuntime.version()).isEqualTo(jdbc.queryForObject("""
                    SELECT active_version_id FROM platform_configuration_state WHERE id = 1
                    """, Long.class));
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE platform_configuration_versions
                    SET checksum = REPEAT('f', 64) WHERE id = ?
                    """, firstDraft)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE platform_configuration_versions
                    SET created_by = created_by + 1 WHERE id = ?
                    """, firstDraft)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update(
                    "DELETE FROM platform_configuration_versions WHERE id = ?", firstDraft))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM permissions
                    WHERE permission_code LIKE 'system:configuration:%'
                    """, Integer.class)).isEqualTo(4);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1601");
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
