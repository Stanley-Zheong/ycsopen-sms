package com.ycsopen.sms.core.verification;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ycsopen.sms.core.YcsopenSmsCoreApplication;
import com.ycsopen.sms.core.common.security.config.CryptoStorageStartupVerifier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Installed-Chrome Phase 08 acceptance skeleton over the real protected service topology. */
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase08RealServicePlaywrightTest {
    private static final String PASSWORD = "Phase08-Valid!123";
    private static final String ADMIN = "phase08-admin";
    private static final String NO_READ = "phase08-no-read";
    private static final String READ_ONLY = "phase08-read-only";
    private static final String TENANT_ADMIN = "phase08-tenant-admin";

    @Test
    void chromeBootsTheRealQualificationServiceTopology() throws Exception {
        assertThat(runChromeAcceptance("real-smoke"))
                .contains("PHASE08_REAL_SERVICE_CHROME_SMOKE_PASS");
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1 || !("real-smoke".equals(arguments[0])
                || "real-smoke-p09".equals(arguments[0]))) {
            throw new IllegalArgumentException("closed real-service smoke invocation required");
        }
        runRealSmoke("real-smoke-p09".equals(arguments[0]) ? "phase09" : "phase08");
    }

    static String runChromeAcceptance(String childArgument) throws Exception {
        Path root = Phase01ServiceHarness.repositoryRoot();
        Path chrome = Path.of(System.getenv().getOrDefault(
                "YCSOPEN_CHROME_PATH",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
        assertThat(chrome).as("locally installed Google Chrome").isExecutable();
        String output;
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            Phase03ServiceHarness.ServiceSession mysql = fixtures.mysql();
            Phase03ServiceHarness.ServiceSession minio = fixtures.minio();
            Phase03ServiceHarness.SoftHsmHandoff handoff = fixtures.softHsm().softHsm();
            Path destination = handoff.config().getParent().getParent();
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            String classpath = System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path"));
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("SOFTHSM2_CONF", handoff.config().toString());
            environment.put("YCSOPEN_PKCS11_PIN", new String(handoff.userPin()));
            environment.put("PHASE03_SOFTHSM_DESTINATION", destination.toString());
            environment.put("PHASE03_MYSQL_HOST", mysql.host());
            environment.put("PHASE03_MYSQL_PORT", Integer.toString(mysql.port()));
            environment.put("PHASE03_MYSQL_USER", mysql.username());
            environment.put("PHASE03_MYSQL_PASSWORD", mysql.password());
            environment.put("PHASE03_MINIO_HOST", minio.host());
            environment.put("PHASE03_MINIO_PORT", Integer.toString(minio.port()));
            environment.put("PHASE03_MINIO_USER", minio.username());
            environment.put("PHASE03_MINIO_PASSWORD", minio.password());
            environment.put("AWS_ACCESS_KEY_ID", minio.username());
            environment.put("AWS_SECRET_ACCESS_KEY", minio.password());
            environment.put("AWS_EC2_METADATA_DISABLED", "true");
            environment.put("YCSOPEN_CHROME_PATH", chrome.toString());
            output = runChild(root, List.of(java.toString(), "-cp", classpath,
                    Phase08RealServicePlaywrightTest.class.getName(), childArgument), environment);
        }
        return output;
    }

    private static void runRealSmoke() throws Exception {
        runRealSmoke("phase08");
    }

    private static void runRealSmoke(String scenario) throws Exception {
        Path root = Phase01ServiceHarness.repositoryRoot();
        Path destination = Path.of(requiredEnvironment("PHASE03_SOFTHSM_DESTINATION"));
        Phase03ServiceHarness.SoftHsmHandoff handoff = Phase03ServiceHarness.readHandoff(destination);
        Phase03ObjectStorageIntegrationTest.provisionKeys(destination, handoff);

        DataSource dataSource = mysqlDataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .placeholderReplacement(false).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedKeyMetadata(jdbc);

        URI minioEndpoint = URI.create("http://" + requiredEnvironment("PHASE03_MINIO_HOST")
                + ":" + requiredEnvironment("PHASE03_MINIO_PORT"));
        String bucket = scenario + "-smoke-bucket";
        try (S3Client s3 = s3(minioEndpoint);
             SandboxServer notification = SandboxServer.notification();
             SandboxServer inspection = SandboxServer.inspection()) {
            s3.createBucket(request -> request.bucket(bucket));
            try {
                int backendPort = freePort();
                int webPort = freePort();
                Map<String, Object> properties = applicationProperties(
                        handoff, dataSource, minioEndpoint, bucket, notification, inspection,
                        backendPort);
                try (ConfigurableApplicationContext application = new SpringApplicationBuilder(
                        YcsopenSmsCoreApplication.class)
                        .profiles("phase01-integration")
                        .run(properties.entrySet().stream()
                                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                                .toArray(String[]::new))) {
                    seedSmokeData(jdbc, application.getBean(PasswordEncoder.class), scenario);
                    runPlaywright(root, backendPort, webPort, notification, scenario);
                }
            } finally {
                s3.listObjectsV2(request -> request.bucket(bucket)).contents().forEach(object ->
                        s3.deleteObject(request -> request.bucket(bucket).key(object.key())));
                s3.deleteBucket(request -> request.bucket(bucket));
            }
        }
        System.out.println(("phase09".equals(scenario) ? "PHASE09" : "PHASE08")
                + "_REAL_SERVICE_CHROME_SMOKE_PASS topology=mysql,minio,softhsm,spring,vite,chrome,notification,inspection");
    }

    private static Map<String, Object> applicationProperties(
            Phase03ServiceHarness.SoftHsmHandoff handoff, DataSource dataSource,
            URI minioEndpoint, String bucket, SandboxServer notification,
            SandboxServer inspection, int backendPort) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", backendPort);
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.datasource.url", ((DriverManagerDataSource) dataSource).getUrl());
        properties.put("spring.datasource.username", requiredEnvironment("PHASE03_MYSQL_USER"));
        properties.put("spring.datasource.password", requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
        properties.put("spring.flyway.user", requiredEnvironment("PHASE03_MYSQL_USER"));
        properties.put("spring.flyway.password", requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
        properties.put("spring.flyway.placeholder-replacement", false);
        properties.put("spring.jpa.hibernate.ddl-auto", "none");
        properties.put("spring.jpa.open-in-view", false);
        properties.put("spring.quartz.auto-startup", false);
        properties.put("ycsopen.database.runtime-grants.enabled", false);
        properties.put("ycsopen.security.jwt.secret", "phase08-real-service-jwt-secret-0123456789");
        properties.put("logging.level.root", "WARN");

        properties.put("ycsopen.security.crypto-storage.enabled", true);
        properties.put("ycsopen.security.crypto-storage.adapter", "SUN_PKCS11");
        properties.put("ycsopen.security.crypto-storage.provider-id", "pkcs11");
        properties.put("ycsopen.security.crypto-storage.module-path", handoff.library());
        properties.put("ycsopen.security.crypto-storage.allowed-module-paths", handoff.library());
        properties.put("ycsopen.security.crypto-storage.slot-id", Long.toUnsignedString(handoff.slot()));
        properties.put("ycsopen.security.crypto-storage.token-identity", "phase08-real-service");
        properties.put("ycsopen.security.crypto-storage.credential-source", "ENVIRONMENT");
        properties.put("ycsopen.security.crypto-storage.credential-reference", "YCSOPEN_PKCS11_PIN");
        properties.put("ycsopen.security.crypto-storage.mechanisms", "CKM_AES_GCM,CKM_SHA256_HMAC");
        properties.put("ycsopen.security.crypto-storage.key-attributes",
                "CKA_TOKEN,CKA_PRIVATE,CKA_SENSITIVE,CKA_NOT_EXTRACTABLE");
        properties.put("ycsopen.security.crypto-storage.rotation-required-at", 983040);
        properties.put("ycsopen.security.crypto-storage.hard-ceiling", 1048576);
        properties.put("ycsopen.security.crypto-storage.aliases.field-encryption-kek",
                CryptoStorageStartupVerifier.FIELD_KEK_ALIAS);
        properties.put("ycsopen.security.crypto-storage.aliases.snapshot-recovery",
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_ALIAS);
        properties.put("ycsopen.security.crypto-storage.references.snapshot-recovery",
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_REFERENCE);
        properties.put("ycsopen.security.crypto-storage.aliases.mobile-blind-index",
                CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS);
        properties.put("ycsopen.security.crypto-storage.aliases.object-capability-digest",
                CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS);
        properties.put("ycsopen.security.crypto-storage.aliases.registration-upload-digest",
                CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS);
        properties.put("ycsopen.security.crypto-storage.key-descriptors",
                "FIELD_ENCRYPTION_KEK|1|field-kek.v1|ycs.field-encryption-kek.v1,"
                        + "SNAPSHOT_RECOVERY|1|snapshot-recovery.v1|ycs.snapshot-recovery.v1,"
                        + "MOBILE_BLIND_INDEX|1|mobile-index.v1|ycs.mobile-blind-index.v1,"
                        + "OBJECT_CAPABILITY_DIGEST|1|object-digest.v1|ycs.object-capability-digest.v1,"
                        + "REGISTRATION_UPLOAD_DIGEST|1|registration-digest.v1|ycs.registration-upload-digest.v1");

        properties.put("ycsopen.object-store.enabled", true);
        properties.put("ycsopen.object-store.bucket", bucket);
        properties.put("ycsopen.object-store.region", "us-east-1");
        properties.put("ycsopen.object-store.endpoint", minioEndpoint);
        properties.put("ycsopen.object-store.allowed-endpoints", minioEndpoint);
        properties.put("ycsopen.object-store.credential-provider", "DEFAULT_CHAIN");
        properties.put("ycsopen.object-store.path-style-access", true);
        properties.put("ycsopen.object-store.allow-insecure-loopback", true);
        properties.put("ycsopen.platform-notification.enabled", true);
        properties.put("ycsopen.platform-notification.base-url", notification.baseUrl());
        properties.put("ycsopen.platform-notification.path", "/messages");
        properties.put("ycsopen.tenant-qualification.inspection.endpoint",
                inspection.baseUrl() + "/inspect");
        properties.put("ycsopen.tenant-qualification.inspection.credential", "phase08-sandbox");
        return properties;
    }

    private static void seedKeyMetadata(JdbcTemplate jdbc) {
        insertKey(jdbc, "FIELD_ENCRYPTION_KEK", 1, "field-kek.v1", "ACTIVE");
        insertKey(jdbc, "SNAPSHOT_RECOVERY", 1, "snapshot-recovery.v1", "ACTIVE");
        insertKey(jdbc, "MOBILE_BLIND_INDEX", 1, "mobile-index.v1", "ACTIVE");
        insertKey(jdbc, "OBJECT_CAPABILITY_DIGEST", 1, "object-digest.v1", "ACTIVE");
        insertKey(jdbc, "REGISTRATION_UPLOAD_DIGEST", 1, "registration-digest.v1", "ACTIVE");
    }

    private static void insertKey(JdbcTemplate jdbc, String purpose, int version,
                                  String reference, String state) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES (?,?,'pkcs11',?,?)",
                purpose, version, reference, state);
    }

    private static void seedSmokeData(JdbcTemplate jdbc, PasswordEncoder encoder, String scenario) {
        if ("phase09".equals(scenario)) {
            seedPhase09SmokeData(jdbc, encoder);
            return;
        }
        String passwordHash = encoder.encode(PASSWORD);
        long admin = insertUser(jdbc, ADMIN, "ADMIN", passwordHash);
        long noRead = insertUser(jdbc, NO_READ, "OPERATOR", passwordHash);
        long readOnly = insertUser(jdbc, READ_ONLY, "OPERATOR", passwordHash);
        long noReadRole = insertRole(jdbc, "P8_NO_READ", "Phase 08 no read");
        long readOnlyRole = insertRole(jdbc, "P8_READ_ONLY", "Phase 08 read only");
        assignRole(jdbc, noRead, noReadRole, admin);
        assignRole(jdbc, readOnly, readOnlyRole, admin);
        assignPermission(jdbc, readOnlyRole, "tenant:menu");
        assignPermission(jdbc, readOnlyRole, "tenant:read");
    }

    private static void seedPhase09SmokeData(JdbcTemplate jdbc, PasswordEncoder encoder) {
        String passwordHash = encoder.encode("Phase09-Valid!123");
        jdbc.update("DELETE FROM tenant_api_keys WHERE tenant_id=?", 9001L);
        jdbc.update("DELETE FROM tenant_protocol_credentials WHERE tenant_id=?", 9001L);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'phase09-%')");
        jdbc.update("DELETE FROM users WHERE username LIKE 'phase09-%'");
        jdbc.update("DELETE FROM roles WHERE role_code IN ('TENANT_USER','TENANT_DEV') AND tenant_id=?", 9001L);
        jdbc.update("DELETE FROM tenants WHERE id=?", 9001L);
        jdbc.update("DELETE FROM tenants WHERE tenant_no=?", "P09-9002");
        jdbc.update("INSERT INTO tenants(id,tenant_no,short_name,full_name,unified_social_credit_code,verification_status,lifecycle_status,inspection_status) VALUES (?,?,?,?,?,'VERIFIED','TRIAL','COMPLETED')",
                9001L, "P09-9001", "P09测试租户", "P09测试租户有限公司", "91350211M000100Y90");
        jdbc.update("INSERT INTO tenants(tenant_no,short_name,full_name,unified_social_credit_code,verification_status,lifecycle_status,inspection_status) VALUES (?,?,?,?, 'VERIFIED','TRIAL','COMPLETED')",
                "P09-9002", "P09隔离租户", "P09隔离租户有限公司", "91350211M000100Y91");
        long foreignTenant = jdbc.queryForObject("SELECT id FROM tenants WHERE tenant_no=?", Long.class, "P09-9002");
        long admin = insertTenantUser(jdbc, "phase09-admin", "TENANT_ADMIN", 9001L, passwordHash);
        insertTenantUser(jdbc, "phase09-dev", "TENANT_DEV", 9001L, passwordHash);
        insertTenantUser(jdbc, "phase09-foreign", "TENANT_DEV", foreignTenant, passwordHash);
        long userRole = insertTenantRole(jdbc, "TENANT_USER", "业务用户", 9001L);
        long devRole = insertTenantRole(jdbc, "TENANT_DEV", "开发者", 9001L);
        assignRole(jdbc, admin, userRole, admin);
        assignRole(jdbc, admin, devRole, admin);
    }

    private static long insertTenantUser(JdbcTemplate jdbc, String username, String type,
                                         long tenantId, String passwordHash) {
        jdbc.update("INSERT INTO users(username,password_hash,real_name,user_type,tenant_id,status,created_by) VALUES (?,?,?,?,?,'ACTIVE','phase09-smoke')",
                username, passwordHash, username, type, tenantId);
        return jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
    }

    private static long insertTenantRole(JdbcTemplate jdbc, String code, String name, long tenantId) {
        jdbc.update("INSERT INTO roles(role_code,role_name,role_type,tenant_id,status) VALUES (?,?,'TENANT',?,'ACTIVE')",
                code, name, tenantId);
        return jdbc.queryForObject("SELECT id FROM roles WHERE role_code=?", Long.class, code);
    }

    private static long insertUser(JdbcTemplate jdbc, String username, String userType,
                                   String passwordHash) {
        jdbc.update("""
                INSERT INTO users(username,password_hash,real_name,user_type,status,created_by)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'phase08-smoke')
                """, username, passwordHash, username, userType);
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private static long insertRole(JdbcTemplate jdbc, String code, String name) {
        jdbc.update("""
                INSERT INTO roles(role_code,role_name,role_type,status)
                VALUES (?,?,'PLATFORM','ACTIVE')
                """, code, name);
        return jdbc.queryForObject("SELECT id FROM roles WHERE role_code = ?", Long.class, code);
    }

    private static void assignRole(JdbcTemplate jdbc, long userId, long roleId, long actorId) {
        jdbc.update("INSERT INTO user_roles(user_id,role_id,granted_by) VALUES (?,?,?)",
                userId, roleId, String.valueOf(actorId));
    }

    private static void assignPermission(JdbcTemplate jdbc, long roleId, String permission) {
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT ?,id FROM permissions WHERE permission_code=?
                """, roleId, permission);
    }

    private static void runPlaywright(Path root, int backendPort, int webPort,
                                      SandboxServer notification, String scenario)
            throws IOException, InterruptedException {
        boolean phase09 = "phase09".equals(scenario);
        Path report = root.resolve(phase09
                ? ".planning/phases/09-tenant-access-administration/EVIDENCE/phase09-playwright-raw.json"
                : ".planning/phases/08-tenant-qualification-status/EVIDENCE/phase08-playwright-raw.json");
        Path log = root.resolve(phase09 ? "core/target/phase09-playwright.log"
                : "core/target/phase08-playwright.log");
        Files.createDirectories(report.getParent());
        Files.deleteIfExists(report);
        ProcessBuilder builder = new ProcessBuilder(
                "npm", "run", "test:e2e", "--",
                phase09 ? "tenant-access.spec.ts" : "tenant-qualification.spec.ts",
                "--reporter=json", "--workers=1");
        builder.directory(root.resolve("web").toFile());
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("YCSOPEN_CHROME_PATH", requiredEnvironment("YCSOPEN_CHROME_PATH"));
        environment.put("YCSOPEN_WEB_PORT", Integer.toString(webPort));
        environment.put("YCSOPEN_E2E_ISOLATED", "true");
        environment.put("VITE_BACKEND_TARGET", "http://127.0.0.1:" + backendPort);
        if (phase09) {
            environment.put("PHASE09_TEST_PASSWORD", "Phase09-Valid!123");
            environment.put("PHASE09_ADMIN_USERNAME", "phase09-admin");
            environment.put("PHASE09_DEV_USERNAME", "phase09-dev");
            environment.put("PHASE09_FOREIGN_USERNAME", "phase09-foreign");
        } else {
            environment.put("PHASE08_TEST_PASSWORD", PASSWORD);
            environment.put("PHASE08_ADMIN_USERNAME", ADMIN);
            environment.put("PHASE08_NO_READ_USERNAME", NO_READ);
            environment.put("PHASE08_READ_ONLY_USERNAME", READ_ONLY);
            environment.put("PHASE08_TENANT_USERNAME", TENANT_ADMIN);
            environment.put("PHASE08_NOTIFICATION_INSPECTION_URL",
                    notification.baseUrl() + "/test/latest-code");
        }
        environment.put("PLAYWRIGHT_JSON_OUTPUT_NAME", report.toString());
        builder.environment().putAll(environment);
        Map<String, String> secrets = phase09
                ? Map.of("PHASE09_TEST_PASSWORD", "Phase09-Valid!123")
                : Map.of("PHASE08_TEST_PASSWORD", PASSWORD);
        OwnedProcess.Result result = OwnedProcess.run(builder, Duration.ofMinutes(4), log, secrets);
        if (result.timedOut()) {
            throw new AssertionError("Phase 08 Playwright smoke timed out: " + result.output());
        }
        assertThat(result.exitCode()).as("Phase 08 Playwright smoke: %s", result.output()).isZero();
        assertThat(report).exists();
        assertThat(Files.readString(report))
                .contains(phase09 ? "pw-p9-tenant-administrators-create" : "pw-p8-register",
                        phase09 ? "pw-p9-tenant-administrators-page" : "pw-p8-status-action")
                .doesNotContain("\"status\": \"failed\"", "\"status\":\"failed\"");
    }

    private static S3Client s3(URI endpoint) {
        return S3Client.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        requiredEnvironment("PHASE03_MINIO_USER"),
                        requiredEnvironment("PHASE03_MINIO_PASSWORD"))))
                .httpClient(UrlConnectionHttpClient.create()).build();
    }

    private static DataSource mysqlDataSource() {
        String url = "jdbc:mysql://" + requiredEnvironment("PHASE03_MYSQL_HOST") + ":"
                + requiredEnvironment("PHASE03_MYSQL_PORT") + "/phase01"
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        return new DriverManagerDataSource(url, requiredEnvironment("PHASE03_MYSQL_USER"),
                requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Phase 08 integration environment unavailable: " + name);
        }
        return value;
    }

    private static String runChild(Path root, List<String> command, Map<String, String> environment)
            throws IOException, InterruptedException {
        Path log = root.resolve("core/target/phase08-real-service-child.log");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(root.toFile());
        builder.environment().putAll(environment);
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("test-password", PASSWORD);
        for (String name : List.of("PHASE03_MYSQL_PASSWORD", "PHASE03_MINIO_PASSWORD",
                "YCSOPEN_PKCS11_PIN", "AWS_SECRET_ACCESS_KEY")) {
            secrets.put(name, environment.get(name));
        }
        OwnedProcess.Result result = OwnedProcess.run(
                builder, Duration.ofMinutes(6), log, secrets);
        if (result.timedOut()) {
            throw new AssertionError("Phase 08 child timed out: " + result.output());
        }
        if (result.exitCode() != 0) {
            throw new AssertionError("Phase 08 child failed: " + result.output());
        }
        return result.output().strip();
    }

    /** Run-owned process boundary with bounded diagnostics and unconditional tree cleanup. */
    static final class OwnedProcess {
        static final int TAIL_LIMIT = 12_000;
        private static final Duration GRACEFUL_WAIT = Duration.ofMillis(500);
        private static final Duration FORCE_WAIT = Duration.ofSeconds(2);
        private static final long SNAPSHOT_INTERVAL_MILLIS = 25;

        private OwnedProcess() {
        }

        static Result run(ProcessBuilder builder, Duration timeout, Path diagnostic,
                          Map<String, String> secretValues)
                throws IOException, InterruptedException {
            Files.createDirectories(diagnostic.getParent());
            Files.deleteIfExists(diagnostic);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            BoundedTail tail = new BoundedTail(TAIL_LIMIT);
            AtomicReference<IOException> drainFailure = new AtomicReference<>();
            Thread drainer = Thread.ofPlatform().daemon(true)
                    .name("phase08-process-output-" + process.pid())
                    .start(() -> drain(process.getInputStream(), tail, drainFailure));
            Set<ProcessHandle> processTree = new LinkedHashSet<>();
            boolean completed = false;
            boolean timedOut = false;
            int exitCode = -1;
            InterruptedException interruptedWait = null;
            long deadline = System.nanoTime() + timeout.toNanos();
            try {
                while (!completed) {
                    snapshot(process, processTree);
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        timedOut = true;
                        break;
                    }
                    long waitMillis = Math.max(1, Math.min(SNAPSHOT_INTERVAL_MILLIS,
                            TimeUnit.NANOSECONDS.toMillis(remaining)));
                    completed = process.waitFor(waitMillis, TimeUnit.MILLISECONDS);
                }
                if (completed) {
                    exitCode = process.exitValue();
                }
            } catch (InterruptedException failure) {
                interruptedWait = failure;
            }

            snapshot(process, processTree);
            Cleanup cleanup = stopAndReap(process, processTree);
            boolean drainInterrupted = awaitDrainer(drainer, process.getInputStream());
            IOException outputFailure = drainFailure.get();
            String output = sanitize(tail.text(), secretValues);
            boolean interrupted = interruptedWait != null || cleanup.interrupted()
                    || drainInterrupted;
            boolean failed = interrupted || timedOut || exitCode != 0
                    || !cleanup.survivors().isEmpty() || outputFailure != null;
            if (failed) {
                Files.writeString(diagnostic, output, StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(diagnostic);
            }

            if (interrupted) {
                InterruptedException failure = interruptedWait == null
                        ? new InterruptedException("Phase 08 process cleanup interrupted")
                        : interruptedWait;
                if (!cleanup.survivors().isEmpty()) {
                    failure.addSuppressed(new IOException(
                            "Phase 08 process survivors: " + cleanup.survivors()));
                }
                if (outputFailure != null) {
                    failure.addSuppressed(outputFailure);
                }
                Thread.currentThread().interrupt();
                throw failure;
            }
            if (!cleanup.survivors().isEmpty()) {
                throw new IOException("Phase 08 process cleanup failed: " + cleanup.survivors());
            }
            if (outputFailure != null) {
                throw outputFailure;
            }
            return new Result(exitCode, timedOut, output, cleanup.forcedProcessCount());
        }

        private static void snapshot(Process process, Set<ProcessHandle> processTree) {
            process.descendants().forEach(processTree::add);
            processTree.add(process.toHandle());
        }

        private static Cleanup stopAndReap(Process process, Set<ProcessHandle> processTree) {
            List<ProcessHandle> ordered = new ArrayList<>(processTree);
            ordered.sort((left, right) -> left.pid() == process.pid() ? 1
                    : right.pid() == process.pid() ? -1 : 0);
            ordered.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
            AwaitResult graceful = awaitExit(ordered, GRACEFUL_WAIT);
            List<ProcessHandle> forced = ordered.stream().filter(ProcessHandle::isAlive).toList();
            forced.forEach(ProcessHandle::destroyForcibly);
            AwaitResult forceful = awaitExit(forced, FORCE_WAIT);
            boolean interrupted = graceful.interrupted() || forceful.interrupted();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            try {
                process.waitFor(FORCE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
            List<Long> survivors = ordered.stream().filter(ProcessHandle::isAlive)
                    .map(ProcessHandle::pid).toList();
            return new Cleanup(interrupted, forced.size(), survivors);
        }

        private static AwaitResult awaitExit(List<ProcessHandle> processes, Duration timeout) {
            boolean interrupted = false;
            long deadline = System.nanoTime() + timeout.toNanos();
            while (processes.stream().anyMatch(ProcessHandle::isAlive)
                    && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            return new AwaitResult(interrupted);
        }

        private static boolean awaitDrainer(Thread drainer, InputStream input) throws IOException {
            boolean interrupted = false;
            long deadline = System.nanoTime() + FORCE_WAIT.toNanos();
            while (drainer.isAlive() && System.nanoTime() < deadline) {
                try {
                    drainer.join(10);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (drainer.isAlive()) {
                input.close();
                while (drainer.isAlive() && System.nanoTime() < deadline + FORCE_WAIT.toNanos()) {
                    try {
                        drainer.join(10);
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
            }
            if (drainer.isAlive()) {
                throw new IOException("Phase 08 process output drainer did not terminate");
            }
            return interrupted;
        }

        private static void drain(InputStream input, BoundedTail tail,
                                  AtomicReference<IOException> failure) {
            byte[] buffer = new byte[4096];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    tail.append(buffer, read);
                }
            } catch (IOException exception) {
                failure.set(exception);
            }
        }

        private static String sanitize(String raw, Map<String, String> secretValues) {
            String sanitized = raw.replaceAll(
                    "(?i)(password|secret|pin)\\s*[=:]\\s*\\S+", "$1=[REDACTED]");
            for (String value : secretValues.values()) {
                if (value != null && !value.isEmpty()) {
                    sanitized = sanitized.replace(value, "[REDACTED]");
                }
            }
            byte[] bytes = sanitized.getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= TAIL_LIMIT) {
                return sanitized;
            }
            return new String(bytes, bytes.length - TAIL_LIMIT, TAIL_LIMIT,
                    StandardCharsets.UTF_8);
        }

        record Result(int exitCode, boolean timedOut, String output, int forcedProcessCount) {
        }

        private record Cleanup(boolean interrupted, int forcedProcessCount, List<Long> survivors) {
        }

        private record AwaitResult(boolean interrupted) {
        }

        private static final class BoundedTail {
            private final byte[] bytes;
            private int start;
            private int size;

            private BoundedTail(int capacity) {
                bytes = new byte[capacity];
            }

            synchronized void append(byte[] source, int length) {
                for (int index = 0; index < length; index++) {
                    if (size < bytes.length) {
                        bytes[(start + size) % bytes.length] = source[index];
                        size++;
                    } else {
                        bytes[start] = source[index];
                        start = (start + 1) % bytes.length;
                    }
                }
            }

            synchronized String text() {
                byte[] result = new byte[size];
                for (int index = 0; index < size; index++) {
                    result[index] = bytes[(start + index) % bytes.length];
                }
                return new String(result, StandardCharsets.UTF_8);
            }
        }
    }

    private static final class SandboxServer implements AutoCloseable {
        private static final Pattern CONTACT_CODE = Pattern.compile("您的验证码为(\\d{6})");
        private final HttpServer server;
        private final ExecutorService executor;

        private SandboxServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static SandboxServer notification() throws IOException {
            AtomicReference<String> latestCode = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.setExecutor(executor);
            server.createContext("/messages", exchange -> {
                try {
                    String request = new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8);
                    Matcher matcher = CONTACT_CODE.matcher(request);
                    if (!matcher.find()) {
                        respond(exchange, 400, "{\"error\":\"missing sandbox code\"}");
                    } else {
                        latestCode.set(matcher.group(1));
                        respond(exchange, 200,
                                "{\"messageId\":\"phase08-notification-sandbox\"}");
                    }
                } finally {
                    exchange.close();
                }
            });
            server.createContext("/test/latest-code", exchange -> {
                try {
                    exchange.getRequestBody().readAllBytes();
                    String code = latestCode.get();
                    respond(exchange, code == null ? 404 : 200,
                            code == null ? "{\"error\":\"not delivered\"}"
                                    : "{\"code\":\"" + code + "\"}");
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return new SandboxServer(server, executor);
        }

        static SandboxServer inspection() throws IOException {
            return start("/inspect", exchange -> respond(exchange, 200,
                    "{\"companyName\":\"八期真实服务机构有限公司\","
                            + "\"creditCode\":\"91350211M000100Y46\","
                            + "\"confidence\":0.99,\"requestId\":\"phase08-inspection-sandbox\"}"));
        }

        private static SandboxServer start(String path, ExchangeHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.setExecutor(executor);
            server.createContext(path, exchange -> {
                try {
                    exchange.getRequestBody().readAllBytes();
                    handler.handle(exchange);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return new SandboxServer(server, executor);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private static void respond(HttpExchange exchange, int status, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }

        @FunctionalInterface
        private interface ExchangeHandler {
            void handle(HttpExchange exchange) throws IOException;
        }
    }
}
