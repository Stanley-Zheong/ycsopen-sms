package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.YcsopenSmsCoreApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Installed-Chrome acceptance against real Spring APIs and a disposable migrated MySQL. */
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase07RealServicePlaywrightTest {
    private static final String PASSWORD = "Phase07-Valid!123";

    @Test
    void chromeExercisesConfigurationThroughRealServiceBoundaries() throws Exception {
        Path root = Phase01ServiceHarness.repositoryRoot();
        Path chrome = Path.of(System.getenv().getOrDefault(
                "YCSOPEN_CHROME_PATH",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
        assertThat(chrome).as("locally installed Google Chrome").isExecutable();
        assertThat(Files.readString(root.resolve("web/test/scripts/system-configuration.spec.ts")))
                .as("production acceptance must not substitute platform requests")
                .doesNotContain("page.route(", ".route('", ".route(\"");

        try (Phase01ServiceSession mysql = Phase01ServiceHarness.startMySql()) {
            String migrator = "phase07_migrator";
            String migratorPassword = "Phase07MigrationOnly!";
            String runtime = "phase07_runtime";
            String runtimePassword = "Phase07RuntimeOnly!";
            rootSql(mysql, """
                    SET GLOBAL log_bin_trust_function_creators=ON;
                    CREATE USER '%s'@'%%' IDENTIFIED BY '%s';
                    CREATE USER '%s'@'%%' IDENTIFIED BY '%s';
                    GRANT ALL PRIVILEGES ON phase01.* TO '%s'@'%%' WITH GRANT OPTION;
                    FLUSH PRIVILEGES;
                    """.formatted(migrator, migratorPassword, runtime, runtimePassword, migrator));

            int backendPort = freePort();
            int webPort = freePort();
            Map<String, Object> properties = Map.ofEntries(
                    Map.entry("server.port", backendPort),
                    Map.entry("spring.main.banner-mode", "off"),
                    Map.entry("spring.datasource.url", mysql.jdbcUrl()),
                    Map.entry("spring.datasource.username", runtime),
                    Map.entry("spring.datasource.password", runtimePassword),
                    Map.entry("spring.flyway.user", migrator),
                    Map.entry("spring.flyway.password", migratorPassword),
                    Map.entry("spring.flyway.placeholder-replacement", false),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "none"),
                    Map.entry("spring.quartz.auto-startup", false),
                    Map.entry("ycsopen.database.runtime-grants.enabled", true),
                    Map.entry("ycsopen.database.runtime-grants.host", "%"),
                    Map.entry("ycsopen.object-store.enabled", false),
                    Map.entry("ycsopen.security.crypto-storage.enabled", false),
                    Map.entry("ycsopen.security.jwt.secret", "phase07-real-service-jwt-secret-0123456789"),
                    Map.entry("logging.level.root", "WARN"));

            try (ConfigurableApplicationContext application = new SpringApplicationBuilder(
                    YcsopenSmsCoreApplication.class)
                    .profiles("phase01-integration")
                    .run(properties.entrySet().stream()
                            .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                            .toArray(String[]::new))) {
                JdbcTemplate jdbc = new JdbcTemplate(application.getBean(DataSource.class));
                seedAccountsAndPermissions(jdbc, application.getBean(PasswordEncoder.class));
                seedSafeReloadRejection(jdbc);
                runPlaywright(root, chrome, backendPort, webPort);
            }
        }
    }

    private static void seedAccountsAndPermissions(JdbcTemplate jdbc, PasswordEncoder encoder) {
        String passwordHash = encoder.encode(PASSWORD);
        long admin = insertUser(jdbc, "phase07-admin", "ADMIN", passwordHash);
        long noRead = insertUser(jdbc, "phase07-no-read", "OPERATOR", passwordHash);
        long readOnly = insertUser(jdbc, "phase07-read-only", "OPERATOR", passwordHash);
        long writer = insertUser(jdbc, "phase07-writer", "OPERATOR", passwordHash);

        long emptyRole = insertRole(jdbc, "P7_NO_READ", "Phase 07 no read");
        long readRole = insertRole(jdbc, "P7_READ_ONLY", "Phase 07 read only");
        long writeRole = insertRole(jdbc, "P7_WRITE_NO_ACTIVATE", "Phase 07 writer");
        assignRole(jdbc, noRead, emptyRole, admin);
        assignRole(jdbc, readOnly, readRole, admin);
        assignRole(jdbc, writer, writeRole, admin);
        assignPermission(jdbc, readRole, "system:configuration:menu");
        assignPermission(jdbc, readRole, "system:configuration:read");
        assignPermission(jdbc, writeRole, "system:configuration:menu");
        assignPermission(jdbc, writeRole, "system:configuration:read");
        assignPermission(jdbc, writeRole, "system:configuration:write");
    }

    private static long insertUser(JdbcTemplate jdbc, String username, String userType,
                                   String passwordHash) {
        jdbc.update("""
                INSERT INTO users(username, password_hash, real_name, user_type, status, created_by)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'phase07-verification')
                """, username, passwordHash, username, userType);
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private static long insertRole(JdbcTemplate jdbc, String code, String name) {
        jdbc.update("""
                INSERT INTO roles(role_code, role_name, role_type, status)
                VALUES (?, ?, 'PLATFORM', 'ACTIVE')
                """, code, name);
        return jdbc.queryForObject("SELECT id FROM roles WHERE role_code = ?", Long.class, code);
    }

    private static void assignRole(JdbcTemplate jdbc, long userId, long roleId, long actorId) {
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_id, granted_by)
                VALUES (?, ?, ?)
                """, userId, roleId, String.valueOf(actorId));
    }

    private static void assignPermission(JdbcTemplate jdbc, long roleId, String code) {
        jdbc.update("""
                INSERT INTO role_permissions(role_id, permission_id)
                SELECT ?, id FROM permissions WHERE permission_code = ?
                """, roleId, code);
    }

    private static void seedSafeReloadRejection(JdbcTemplate jdbc) {
        long actor = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'phase07-admin'", Long.class);
        jdbc.update("""
                INSERT INTO platform_configuration_versions
                  (base_version_id, status, values_json, changed_keys_json, checksum, reason,
                   created_by, reload_status, reload_error_code)
                VALUES (NULL, 'RELOAD_REJECTED',
                  '{"security.login.max-failures":"7","security.login.unusual-ip-enabled":"true","security.export.signing-key-ref":"env:YCS_SMS_EXPORT_SIGNING_KEY"}',
                  '["security.login.max-failures"]', REPEAT('0', 64), 'rejected fixture', ?,
                  'REJECTED', 'TEST_REJECTED')
                """, actor);
    }

    private static void runPlaywright(Path root, Path chrome, int backendPort, int webPort)
            throws IOException, InterruptedException {
        Path evidence = root.resolve(
                ".planning/phases/07-platform-system-configuration/EVIDENCE/phase07-playwright-raw.json");
        Path processLog = root.resolve("core/target/phase07-playwright.log");
        Files.createDirectories(evidence.getParent());
        Files.createDirectories(processLog.getParent());
        Files.deleteIfExists(evidence);

        ProcessBuilder builder = new ProcessBuilder(
                "npm", "run", "test:e2e", "--", "system-configuration.spec.ts",
                "--reporter=json", "--workers=1");
        builder.directory(root.resolve("web").toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(processLog.toFile());
        builder.environment().putAll(Map.of(
                "YCSOPEN_CHROME_PATH", chrome.toString(),
                "YCSOPEN_WEB_PORT", String.valueOf(webPort),
                "YCSOPEN_E2E_ISOLATED", "true",
                "VITE_BACKEND_TARGET", "http://127.0.0.1:" + backendPort,
                "PHASE07_TEST_PASSWORD", PASSWORD,
                "PLAYWRIGHT_JSON_OUTPUT_NAME", evidence.toString()));
        Process process = builder.start();
        boolean finished = process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroyForcibly();
            throw new AssertionError("Phase 07 Playwright acceptance timed out; log: "
                    + boundedLog(processLog));
        }
        assertThat(process.exitValue())
                .as("Phase 07 Playwright acceptance output: %s", boundedLog(processLog))
                .isZero();
        assertThat(evidence).exists();
        String report = Files.readString(evidence);
        assertThat(report)
                .contains("pw-p7-system-configuration C-P7-SYSTEM-CONFIG OBL-IA-ADMIN-SYSTEM-CONFIG")
                .doesNotContain("\"status\": \"failed\"", "\"status\":\"failed\"");
    }

    private static String boundedLog(Path path) throws IOException {
        String output = Files.exists(path) ? Files.readString(path) : "<missing>";
        return output.length() <= 12_000 ? output : output.substring(output.length() - 12_000);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
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
