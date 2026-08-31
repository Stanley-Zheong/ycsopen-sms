package com.ycsopen.sms.core.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OBL-FOUND-TRACE-003: verifies the production MySQL dialect and Spring wiring.
 * The schema is disposable and synthetic; this test never adds a Flyway migration.
 */
@SpringBootTest(
        classes = Phase01MySqlIntegrationTest.MySqlVerificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
class Phase01MySqlIntegrationTest {

    private static final String EXPECTED_MIGRATION_SHA256 =
            "fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9";
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([1-9][0-9]*)__.+\\.sql$");
    private static Phase01ServiceSession mysql;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        mysql = Phase01ServiceHarness.startMySql();
        registry.add("spring.datasource.url", mysql::jdbcUrl);
        registry.add("spring.datasource.username", mysql::username);
        registry.add("spring.datasource.password", mysql::password);
    }

    @AfterAll
    static void stopMySql() {
        if (mysql != null) {
            mysql.close();
        }
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    Flyway flyway;

    @Test
    void appliesDeclaredMigrationsWithImmutableV1AndBindsRealServerIdentity() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("phase01");
        assertThat(jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class))
                .startsWith(mysql.username() + "@");
        assertThat(jdbcTemplate.queryForObject("SELECT @@version", String.class)).startsWith("8.4.11");
        assertThat(jdbcTemplate.queryForObject("SELECT @@character_set_connection", String.class))
                .isEqualTo("utf8mb4");

        Path migrationDirectory = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/src/main/resources/db/migration");
        List<String> declaredVersions = declaredMigrationVersions(migrationDirectory);
        List<String> appliedVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history " +
                        "WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(declaredVersions).contains("1");
        assertThat(appliedVersions).containsExactlyElementsOf(declaredVersions);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT checksum FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class)).isNotNull();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        Path migration = migrationDirectory.resolve("V1__init_schema.sql");
        assertThat(sha256(migration)).isEqualTo(EXPECTED_MIGRATION_SHA256);
        assertThat(mysql.migrationSha256()).isEqualTo(EXPECTED_MIGRATION_SHA256);
        assertThat(mysql.imageDigest()).isEqualTo(
                "sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb");
        assertThat(mysql.platformImageDigest()).isEqualTo(mysql.containerImageDigest());
        assertThat(mysql.platform()).isEqualTo(mysql.containerPlatform());
    }

    @Test
    void roundTripsSimplifiedChineseInsideARolledBackSpringTransaction() {
        String table = "phase01_verification_transaction";
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
        jdbcTemplate.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, content VARCHAR(64) NOT NULL) " +
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO " + table + " (id, content) VALUES (?, ?)",
                        1L, "阶段一合成验证");
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT content FROM " + table + " WHERE id = 1", String.class))
                        .isEqualTo("阶段一合成验证");
                status.setRollbackOnly();
            });
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class))
                    .isZero();
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static List<String> declaredMigrationVersions(Path migrationDirectory) throws IOException {
        try (Stream<Path> files = Files.list(migrationDirectory)) {
            List<String> versions = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql"))
                    .map(VERSIONED_MIGRATION::matcher)
                    .peek(matcher -> {
                        if (!matcher.matches()) {
                            throw new IllegalStateException("Unsupported versioned Flyway migration filename");
                        }
                    })
                    .map(matcher -> matcher.group(1))
                    .sorted((left, right) -> Integer.compare(Integer.parseInt(left), Integer.parseInt(right)))
                    .toList();
            assertThat(versions).doesNotHaveDuplicates();
            return versions;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    static class MySqlVerificationApplication {
    }
}

/** Shared test-only bridge to the fixed-argv Ruby service harness. */
final class Phase01ServiceHarness {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Path ROOT = locateRepositoryRoot();
    private static final Path SCRIPT = ROOT.resolve("scripts/lib/phase-01/service_checks.rb");
    private static final long COMMAND_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3);
    private static final int OUTPUT_LIMIT_BYTES = 1_048_576;
    private static final long DRAIN_GRACE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final long TERMINATION_GRACE_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int CLEANUP_ATTEMPTS = 3;
    private static final String PROCESS_SCOPE_ENV = "YCSOPEN_PHASE01_PROCESS_SCOPE";
    private static final long SCOPE_SCAN_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(2);

    private Phase01ServiceHarness() {
    }

    static Path repositoryRoot() {
        return ROOT;
    }

    static Phase01ServiceSession startMySql() {
        String runId = "mysql-" + randomHex(6);
        String username = "phase01_" + randomHex(4);
        String password = randomSecret();
        String rootPassword = randomSecret();
        boolean handedOff = false;
        try {
            JsonNode identity = runJson(SCRIPT,
                    new String[]{"/usr/bin/env", "ruby", SCRIPT.toString(), "start", "--service", "mysql", "--run-id", runId},
                    Map.of(
                            "PHASE01_MYSQL_USER", username,
                            "PHASE01_MYSQL_PASSWORD", password,
                            "PHASE01_MYSQL_ROOT_PASSWORD", rootPassword
                    ), COMMAND_TIMEOUT_MILLIS, OUTPUT_LIMIT_BYTES);
            Phase01ServiceSession session = Phase01ServiceSession.mysql(runId, identity, username, password);
            handedOff = true;
            return session;
        } finally {
            if (!handedOff) {
                cleanupWithRetry(SCRIPT, "mysql", runId, Map.of());
            }
        }
    }

    static Phase01ServiceSession startRedis() {
        String runId = "redis-" + randomHex(6);
        boolean handedOff = false;
        try {
            JsonNode identity = runJson(SCRIPT,
                    new String[]{"/usr/bin/env", "ruby", SCRIPT.toString(), "start", "--service", "redis", "--run-id", runId},
                    Map.of(), COMMAND_TIMEOUT_MILLIS, OUTPUT_LIMIT_BYTES);
            Phase01ServiceSession session = Phase01ServiceSession.redis(runId, identity);
            handedOff = true;
            return session;
        } finally {
            if (!handedOff) {
                cleanupWithRetry(SCRIPT, "redis", runId, Map.of());
            }
        }
    }

    static void stop(String service, String runId) {
        cleanupWithRetry(SCRIPT, service, runId, Map.of());
    }

    static CommandResult runCommandForTest(String[] argv, Map<String, String> environment,
                                           long timeoutMillis, int outputLimit) {
        return execute(argv, environment, timeoutMillis, outputLimit);
    }

    static JsonNode startServiceForTest(Path script, String service, String runId,
                                        Map<String, String> environment, long timeoutMillis, int outputLimit) {
        boolean completed = false;
        try {
            JsonNode identity = runJson(script,
                    new String[]{"/usr/bin/env", "ruby", script.toString(), "start", "--service", service, "--run-id", runId},
                    environment, timeoutMillis, outputLimit);
            completed = true;
            return identity;
        } finally {
            if (!completed) {
                cleanupWithRetry(script, service, runId, environment);
            }
        }
    }

    private static void cleanupWithRetry(Path script, String service, String runId,
                                         Map<String, String> environment) {
        boolean interrupted = Thread.interrupted();
        AssertionError lastFailure = null;
        try {
            for (int attempt = 1; attempt <= CLEANUP_ATTEMPTS; attempt++) {
                try {
                    runJson(script,
                            new String[]{"/usr/bin/env", "ruby", script.toString(), "stop", "--service", service, "--run-id", runId},
                            environment, TimeUnit.SECONDS.toMillis(30), OUTPUT_LIMIT_BYTES);
                    return;
                } catch (AssertionError failure) {
                    lastFailure = failure;
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50L * attempt));
                    Thread.interrupted();
                }
            }
            throw new AssertionError("PHASE01_SERVICE_CLEANUP_RETRY_EXHAUSTED service=" + service + " runId=" + runId,
                    lastFailure);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static JsonNode runJson(Path script, String[] argv, Map<String, String> environment,
                                    long timeoutMillis, int outputLimit) {
        CommandResult result = execute(argv, environment, timeoutMillis, outputLimit);
        try {
            String lastLine = result.stdout().lines().filter(line -> !line.isBlank())
                    .reduce((first, second) -> second).orElseThrow();
            return JSON.readTree(lastLine);
        } catch (IOException | RuntimeException e) {
            throw new AssertionError("PHASE01_SERVICE_JSON_INVALID script=" + script.getFileName(), e);
        }
    }

    private static CommandResult execute(String[] argv, Map<String, String> environment,
                                         long timeoutMillis, int outputLimit) {
        if (argv.length == 0 || timeoutMillis <= 0 || outputLimit <= 0) {
            throw new IllegalArgumentException("PHASE01_SERVICE_COMMAND_BOUNDS_INVALID");
        }
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(ROOT.toFile());
        builder.redirectErrorStream(false);
        builder.environment().putAll(environment);
        ProcessScope scope = new ProcessScope(UUID.randomUUID().toString());
        builder.environment().put(PROCESS_SCOPE_ENV, scope.marker());
        AtomicBoolean overflow = new AtomicBoolean();
        ExecutorService readers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "phase01-service-output-drain");
            thread.setDaemon(true);
            return thread;
        });
        Process process = null;
        Future<OutputSnapshot> stdoutFuture = null;
        Future<OutputSnapshot> stderrFuture = null;
        Set<ProcessHandle> knownTree = new LinkedHashSet<>();
        try {
            process = builder.start();
            knownTree.add(process.toHandle());
            stdoutFuture = readers.submit(new BoundedOutputReader(process.getInputStream(), outputLimit, overflow));
            stderrFuture = readers.submit(new BoundedOutputReader(process.getErrorStream(), outputLimit, overflow));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            long exitedAt = 0;
            while (true) {
                collectProcessTree(process, knownTree);
                if (overflow.get()) {
                    terminateProcessTree(process, knownTree, scope);
                    throw new AssertionError("PHASE01_SERVICE_COMMAND_OUTPUT_OVERFLOW");
                }
                if (!process.isAlive()) {
                    if (stdoutFuture.isDone() && stderrFuture.isDone()) {
                        break;
                    }
                    if (exitedAt == 0) {
                        exitedAt = System.nanoTime();
                    } else if (System.nanoTime() - exitedAt >= DRAIN_GRACE_NANOS) {
                        terminateProcessTree(process, knownTree, scope);
                        throw new AssertionError("PHASE01_SERVICE_COMMAND_DESCENDANT_REMAINED");
                    }
                }
                if (System.nanoTime() >= deadline) {
                    terminateProcessTree(process, knownTree, scope);
                    throw new AssertionError("PHASE01_SERVICE_COMMAND_TIMEOUT");
                }
                Thread.sleep(10);
            }
            collectScopedProcesses(scope, knownTree);
            if (knownTree.stream().anyMatch(ProcessHandle::isAlive)) {
                terminateProcessTree(process, knownTree, scope);
                throw new AssertionError("PHASE01_SERVICE_COMMAND_DESCENDANT_REMAINED");
            }
            OutputSnapshot stdout = getOutput(stdoutFuture);
            OutputSnapshot stderr = getOutput(stderrFuture);
            if (stdout.failure() != null || stderr.failure() != null) {
                throw new AssertionError("PHASE01_SERVICE_PROCESS_IO",
                        stdout.failure() != null ? stdout.failure() : stderr.failure());
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String diagnostic = (stderr.text().isBlank() ? stdout.text() : stderr.text()).trim();
                throw new AssertionError("PHASE01_SERVICE_COMMAND_NONZERO exit=" + exitCode + " output=" + diagnostic);
            }
            return new CommandResult(stdout.text(), stderr.text(), exitCode);
        } catch (IOException e) {
            if (process != null) {
                terminateProcessTree(process, knownTree, scope);
            }
            throw new AssertionError("PHASE01_SERVICE_PROCESS_IO: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            if (process != null) {
                terminateProcessTree(process, knownTree, scope);
            }
            Thread.currentThread().interrupt();
            throw new AssertionError("PHASE01_SERVICE_PROCESS_INTERRUPTED", e);
        } finally {
            if (process != null) {
                collectScopedProcesses(scope, knownTree);
                if (knownTree.stream().anyMatch(ProcessHandle::isAlive)) {
                    terminateProcessTree(process, knownTree, scope);
                }
            }
            readers.shutdownNow();
        }
    }

    private static OutputSnapshot getOutput(Future<OutputSnapshot> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("PHASE01_SERVICE_PROCESS_INTERRUPTED", e);
        } catch (ExecutionException e) {
            throw new AssertionError("PHASE01_SERVICE_OUTPUT_DRAIN_FAILED", e.getCause());
        }
    }

    private static void collectProcessTree(Process process, Set<ProcessHandle> knownTree) {
        knownTree.add(process.toHandle());
        try (Stream<ProcessHandle> descendants = process.toHandle().descendants()) {
            descendants.forEach(knownTree::add);
        }
    }

    private static void terminateProcessTree(Process process, Set<ProcessHandle> knownTree, ProcessScope scope) {
        boolean interrupted = Thread.interrupted();
        try {
            signalAndAwaitTree(process, knownTree, scope, false, System.nanoTime() + TERMINATION_GRACE_NANOS);
            signalAndAwaitTree(process, knownTree, scope, true, System.nanoTime() + TERMINATION_GRACE_NANOS);
            closeQuietly(process.getOutputStream(), process.getInputStream(), process.getErrorStream());
            if (knownTree.stream().anyMatch(ProcessHandle::isAlive)) {
                throw new AssertionError("PHASE01_SERVICE_PROCESS_TREE_SURVIVED_TERMINATION");
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void signalAndAwaitTree(Process process, Set<ProcessHandle> knownTree, ProcessScope scope,
                                           boolean force, long deadline) {
        long nextScopeScan = 0;
        while (System.nanoTime() < deadline) {
            collectProcessTree(process, knownTree);
            if (System.nanoTime() >= nextScopeScan) {
                collectScopedProcesses(scope, knownTree);
                nextScopeScan = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50);
            }
            List<ProcessHandle> alive = knownTree.stream().filter(ProcessHandle::isAlive)
                    .sorted((left, right) -> left.pid() == process.pid() ? 1 : right.pid() == process.pid() ? -1 : 0)
                    .toList();
            if (alive.isEmpty()) {
                return;
            }
            if (force) {
                alive.forEach(ProcessHandle::destroyForcibly);
            } else {
                alive.forEach(ProcessHandle::destroy);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private static void collectScopedProcesses(ProcessScope scope, Set<ProcessHandle> knownTree) {
        Set<Long> scopedPids = Files.isDirectory(Path.of("/proc/self"))
                ? scanProcForScope(scope)
                : scanPsForScope(scope);
        scopedPids.forEach(pid -> ProcessHandle.of(pid).ifPresent(knownTree::add));
    }

    private static Set<Long> scanProcForScope(ProcessScope scope) {
        Set<Long> matches = new LinkedHashSet<>();
        byte[] marker = scope.environmentEntry().getBytes(StandardCharsets.UTF_8);
        try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
            processes.forEach(handle -> {
                Path environment = Path.of("/proc", Long.toString(handle.pid()), "environ");
                try (InputStream input = Files.newInputStream(environment)) {
                    if (streamContains(input, marker)) {
                        matches.add(handle.pid());
                    }
                } catch (IOException | SecurityException ignored) {
                    // Other users' process environments are expected to be unreadable.
                }
            });
        }
        return matches;
    }

    private static Set<Long> scanPsForScope(ProcessScope scope) {
        ProcessBuilder scannerBuilder = new ProcessBuilder("/bin/ps", "eww", "-axo", "pid=,command=");
        scannerBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        ExecutorService scannerReader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "phase01-process-scope-scan");
            thread.setDaemon(true);
            return thread;
        });
        Process scanner = null;
        try {
            scanner = scannerBuilder.start();
            Process activeScanner = scanner;
            Future<Set<Long>> matches = scannerReader.submit(
                    () -> scanPsLines(activeScanner.getInputStream(), scope.environmentEntry()));
            if (!scanner.waitFor(SCOPE_SCAN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                scanner.destroyForcibly();
                throw new AssertionError("PHASE01_SERVICE_PROCESS_SCOPE_SCAN_TIMEOUT");
            }
            if (scanner.exitValue() != 0) {
                throw new AssertionError("PHASE01_SERVICE_PROCESS_SCOPE_SCAN_FAILED exit=" + scanner.exitValue());
            }
            return matches.get(SCOPE_SCAN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            throw new AssertionError("PHASE01_SERVICE_PROCESS_SCOPE_SCAN_IO", e);
        } catch (InterruptedException e) {
            if (scanner != null) {
                scanner.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new AssertionError("PHASE01_SERVICE_PROCESS_INTERRUPTED", e);
        } catch (ExecutionException | TimeoutException e) {
            if (scanner != null) {
                scanner.destroyForcibly();
            }
            throw new AssertionError("PHASE01_SERVICE_PROCESS_SCOPE_SCAN_FAILED", e);
        } finally {
            scannerReader.shutdownNow();
        }
    }

    private static Set<Long> scanPsLines(InputStream input, String markerText) throws IOException {
        Set<Long> matches = new LinkedHashSet<>();
        byte[] marker = markerText.getBytes(StandardCharsets.UTF_8);
        long pid = 0;
        boolean sawPid = false;
        boolean pidComplete = false;
        boolean matchedLine = false;
        int matchedBytes = 0;
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') {
                if (matchedLine && sawPid) {
                    matches.add(pid);
                }
                pid = 0;
                sawPid = false;
                pidComplete = false;
                matchedLine = false;
                matchedBytes = 0;
                continue;
            }
            if (!pidComplete) {
                if (next >= '0' && next <= '9') {
                    sawPid = true;
                    pid = pid * 10 + next - '0';
                } else if (sawPid) {
                    pidComplete = true;
                }
            }
            if (!matchedLine) {
                if (next == Byte.toUnsignedInt(marker[matchedBytes])) {
                    matchedBytes++;
                    matchedLine = matchedBytes == marker.length;
                } else {
                    matchedBytes = next == Byte.toUnsignedInt(marker[0]) ? 1 : 0;
                }
            }
        }
        if (matchedLine && sawPid) {
            matches.add(pid);
        }
        return matches;
    }

    private static boolean streamContains(InputStream input, byte[] marker) throws IOException {
        int matched = 0;
        int next;
        while ((next = input.read()) != -1) {
            if (next == Byte.toUnsignedInt(marker[matched])) {
                matched++;
                if (matched == marker.length) {
                    return true;
                }
            } else {
                matched = next == Byte.toUnsignedInt(marker[0]) ? 1 : 0;
            }
        }
        return false;
    }

    private static void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Termination cleanup is best-effort for pipes; process liveness is authoritative.
            }
        }
    }

    record CommandResult(String stdout, String stderr, int exitCode) {
    }

    private record ProcessScope(String marker) {
        String environmentEntry() {
            return PROCESS_SCOPE_ENV + "=" + marker;
        }
    }

    private record OutputSnapshot(String text, IOException failure) {
    }

    private record BoundedOutputReader(InputStream input, int limit, AtomicBoolean overflow)
            implements Callable<OutputSnapshot> {
        @Override
        public OutputSnapshot call() {
            ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(limit, 16_384));
            byte[] chunk = new byte[16_384];
            try (input) {
                int count;
                while ((count = input.read(chunk)) != -1) {
                    int remaining = limit - captured.size();
                    if (remaining > 0) {
                        captured.write(chunk, 0, Math.min(remaining, count));
                    }
                    if (count > remaining) {
                        overflow.set(true);
                    }
                }
                return new OutputSnapshot(captured.toString(StandardCharsets.UTF_8), null);
            } catch (IOException failure) {
                return new OutputSnapshot(captured.toString(StandardCharsets.UTF_8), failure);
            }
        }
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("scripts/lib/phase-01/service_checks.rb"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root containing service_checks.rb was not found");
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static String randomSecret() {
        return randomHex(24);
    }
}

final class Phase01ServiceSession implements AutoCloseable {
    private static final Map<String, String> INDEX_DIGESTS = Map.of(
            "mysql", "sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb",
            "redis", "sha256:efe6e2625e4601cd7119c4fb48b1c04cf3071f8b1729ede1216ceee8bc99742d"
    );
    private static final Map<String, String> PLATFORM_DIGESTS = Map.of(
            "mysql/linux/amd64", "sha256:1d6b6a8fcee8ff758ff151d017f5203cd06792a0e698f0a593c9dfcb14609cf0",
            "mysql/linux/arm64", "sha256:c9be23757267a888182ff13a633118a84ce7ad360abaa0f12a9c357ddf628b61",
            "redis/linux/amd64", "sha256:596405c58f60e287ce0d71459202aaff26d90d08590106264a5f4cc2c73308d2",
            "redis/linux/arm64", "sha256:33d3f152f0b7c3cca14f1995e1a2071fd25db9211de747edea148b1efab69131"
    );
    private static final Map<String, String> CONFIG_DIGESTS = Map.of(
            "mysql/linux/amd64", "sha256:bced325a4ab7aec848f4688371c7433351dcb5dba26fbcc29c67727d898ae5cb",
            "mysql/linux/arm64", "sha256:5e7e005a680e75d935984d3d9390990d2a709b3ed67e92708e9e6747f1f754c9",
            "redis/linux/amd64", "sha256:06e204e1b5143b5ea8a807ce8aec086d341eec73a8ad3bdfa2401f25a72ceec6",
            "redis/linux/arm64", "sha256:0396eccc4928863bb29bb4097ec06aeb4bf38943ef8ad2cd4957dc4f514592bf"
    );
    private final String service;
    private final String runId;
    private final String host;
    private final int port;
    private final String imageDigest;
    private final String platformImageDigest;
    private final String containerImageDigest;
    private final String platform;
    private final String containerPlatform;
    private final String migrationSha256;
    private final String username;
    private final String password;
    private final Runnable cleanup;
    private final Thread shutdownHook;
    private boolean closed;

    private Phase01ServiceSession(String service, String runId, JsonNode identity,
                                  String username, String password, Runnable cleanup) {
        this.service = service;
        this.runId = runId;
        this.host = identity.path("host").asText();
        this.port = identity.path("port").asInt();
        this.imageDigest = identity.path("image_digest").asText();
        this.platformImageDigest = identity.path("platform_image_digest").asText();
        this.containerImageDigest = identity.path("container_image_digest").asText();
        this.platform = identity.path("platform").asText();
        this.containerPlatform = identity.path("container_platform").asText();
        this.migrationSha256 = identity.path("migration_sha256").asText();
        this.username = username;
        this.password = password;
        this.cleanup = cleanup;
        assertThat(identity.path("status").asText()).isEqualTo("READY");
        assertThat(identity.path("run_id").asText()).isEqualTo(runId);
        assertThat(platform).isIn("linux/amd64", "linux/arm64");
        assertThat(identity.path("index_contains_platform_manifest").asBoolean()).isTrue();
        assertThat(imageDigest).isEqualTo(INDEX_DIGESTS.get(service));
        assertThat(platformImageDigest).isEqualTo(PLATFORM_DIGESTS.get(service + "/" + platform));
        assertThat(identity.path("config_digest").asText()).isEqualTo(CONFIG_DIGESTS.get(service + "/" + platform));
        assertThat(containerImageDigest).isEqualTo(platformImageDigest);
        assertThat(containerPlatform).isEqualTo(platform);
        this.shutdownHook = new Thread(this::close, "phase01-" + service + "-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    static Phase01ServiceSession mysql(String runId, JsonNode identity, String username, String password) {
        return new Phase01ServiceSession("mysql", runId, identity, username, password,
                () -> Phase01ServiceHarness.stop("mysql", runId));
    }

    static Phase01ServiceSession redis(String runId, JsonNode identity) {
        return new Phase01ServiceSession("redis", runId, identity, null, null,
                () -> Phase01ServiceHarness.stop("redis", runId));
    }

    static Phase01ServiceSession forTest(JsonNode identity, Runnable cleanup) {
        return new Phase01ServiceSession("mysql", "mysql-session01", identity, null, null, cleanup);
    }

    String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/phase01" +
                "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci" +
                "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    String imageDigest() {
        return imageDigest;
    }

    String platformImageDigest() {
        return platformImageDigest;
    }

    String containerImageDigest() {
        return containerImageDigest;
    }

    String platform() {
        return platform;
    }

    String containerPlatform() {
        return containerPlatform;
    }

    String migrationSha256() {
        return migrationSha256;
    }

    synchronized boolean closedForTest() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            cleanup.run();
            closed = true;
            if (Thread.currentThread() != shutdownHook) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown already owns this successful cleanup hook.
                }
            }
        }
    }
}

/** Destructive process-boundary tests run without Docker or the integration-test system property. */
class Phase01ServiceHarnessProcessTest {

    @Test
    void exactSuccessReturnsButClosedPipeSetsidDescendantIsContainedAndRejected() throws Exception {
        Phase01ServiceHarness.CommandResult success = Phase01ServiceHarness.runCommandForTest(
                new String[]{"/usr/bin/env", "ruby", "-e", "STDOUT.write('exact-success')"},
                Map.of(), 5_000, 4_096);
        assertThat(success.exitCode()).isZero();
        assertThat(success.stdout()).isEqualTo("exact-success");

        Path directory = Files.createTempDirectory("phase01-java-detached-");
        Path pidFile = directory.resolve("detached-pid");
        String source = """
                child = fork do
                  Process.setsid
                  trap("TERM") { }
                  STDIN.close
                  STDOUT.close
                  STDERR.close
                  File.write(ARGV.fetch(0), Process.pid.to_s)
                  loop { sleep 1 }
                end
                Process.detach(child)
                sleep 0.01 until File.exist?(ARGV.fetch(0))
                exit 0
                """;
        try {
            assertThatThrownBy(() -> Phase01ServiceHarness.runCommandForTest(
                    new String[]{"/usr/bin/env", "ruby", "-e", source, pidFile.toString()},
                    Map.of(), 5_000, 4_096))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("PHASE01_SERVICE_COMMAND_DESCENDANT_REMAINED");
            assertPublishedProcessesGone(pidFile);
        } finally {
            Files.deleteIfExists(pidFile);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void sessionOnlyClosesAfterCleanupSucceedsAndCanRetry() {
        JsonNode identity = new ObjectMapper().createObjectNode()
                .put("status", "READY")
                .put("run_id", "mysql-session01")
                .put("host", "127.0.0.1")
                .put("port", 3306)
                .put("image_digest", "sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb")
                .put("platform_image_digest", "sha256:c9be23757267a888182ff13a633118a84ce7ad360abaa0f12a9c357ddf628b61")
                .put("container_image_digest", "sha256:c9be23757267a888182ff13a633118a84ce7ad360abaa0f12a9c357ddf628b61")
                .put("config_digest", "sha256:5e7e005a680e75d935984d3d9390990d2a709b3ed67e92708e9e6747f1f754c9")
                .put("platform", "linux/arm64")
                .put("container_platform", "linux/arm64")
                .put("index_contains_platform_manifest", true)
                .put("migration_sha256", "fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9");
        AtomicInteger attempts = new AtomicInteger();
        Phase01ServiceSession session = Phase01ServiceSession.forTest(identity, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new AssertionError("synthetic first cleanup failure");
            }
        });
        assertThatThrownBy(session::close).hasMessageContaining("synthetic first cleanup failure");
        assertThat(session.closedForTest()).isFalse();
        session.close();
        assertThat(session.closedForTest()).isTrue();
        assertThat(attempts).hasValue(2);
    }

    @Test
    void capsFloodedOutputAndTerminatesTheForkedTree() throws Exception {
        Path directory = Files.createTempDirectory("phase01-java-flood-");
        Path pids = directory.resolve("pids");
        String source = """
                trap("TERM") { }
                child = fork do
                  trap("TERM") { }
                  loop { STDOUT.write("x" * 16_384); STDOUT.flush }
                end
                File.write(ARGV.fetch(0), [Process.pid, child].join(","))
                loop { STDERR.write("e" * 16_384); STDERR.flush }
                """;
        try {
            assertThatThrownBy(() -> Phase01ServiceHarness.runCommandForTest(
                    new String[]{"/usr/bin/env", "ruby", "-e", source, pids.toString()},
                    Map.of(), 5_000, 1_024))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("PHASE01_SERVICE_COMMAND_OUTPUT_OVERFLOW");
            assertPublishedProcessesGone(pids);
        } finally {
            Files.deleteIfExists(pids);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void timeoutAndInterruptionBothReapIgnoringDescendants() throws Exception {
        Path directory = Files.createTempDirectory("phase01-java-tree-");
        try {
            Path timeoutPids = directory.resolve("timeout-pids");
            String source = ignoringTreeSource();
            assertThatThrownBy(() -> Phase01ServiceHarness.runCommandForTest(
                    new String[]{"/usr/bin/env", "ruby", "-e", source, timeoutPids.toString()},
                    Map.of(), 250, 4_096))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("PHASE01_SERVICE_COMMAND_TIMEOUT");
            assertPublishedProcessesGone(timeoutPids);

            Path interruptedPids = directory.resolve("interrupted-pids");
            Thread testThread = Thread.currentThread();
            Thread interrupter = new Thread(() -> {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(150));
                testThread.interrupt();
            }, "phase01-test-interrupter");
            interrupter.start();
            try {
                assertThatThrownBy(() -> Phase01ServiceHarness.runCommandForTest(
                        new String[]{"/usr/bin/env", "ruby", "-e", source, interruptedPids.toString()},
                        Map.of(), 5_000, 4_096))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("PHASE01_SERVICE_PROCESS_INTERRUPTED");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
                interrupter.join();
            }
            assertPublishedProcessesGone(interruptedPids);
        } finally {
            try (Stream<Path> children = Files.list(directory)) {
                children.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Test cleanup only.
                    }
                });
            }
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void failedStartRunsExactCleanupRetryAndLeavesNoOwnedResources() throws Exception {
        Path directory = Files.createTempDirectory("phase01-java-cleanup-");
        Path script = directory.resolve("fake-service.rb");
        Path commandLog = directory.resolve("commands.log");
        Path attempts = directory.resolve("attempts");
        Path container = directory.resolve("container");
        Path network = directory.resolve("network");
        Path pids = directory.resolve("pids");
        Files.writeString(script, """
                command = ARGV.shift
                File.open(ENV.fetch("COMMAND_LOG"), "a") { |file| file.puts(([command] + ARGV).join(" ")) }
                if command == "start"
                  File.write(ENV.fetch("CONTAINER_MARKER"), "created")
                  File.write(ENV.fetch("NETWORK_MARKER"), "created")
                  trap("TERM") { }
                  child = fork do
                    trap("TERM") { }
                    loop { sleep 1 }
                  end
                  File.write(ENV.fetch("PID_FILE"), [Process.pid, child].join(","))
                  Process.wait(child)
                elsif command == "stop"
                  count = File.exist?(ENV.fetch("ATTEMPTS")) ? File.read(ENV.fetch("ATTEMPTS")).to_i : 0
                  File.write(ENV.fetch("ATTEMPTS"), (count + 1).to_s)
                  exit 9 if count.zero?
                  File.delete(ENV.fetch("CONTAINER_MARKER")) if File.exist?(ENV.fetch("CONTAINER_MARKER"))
                  File.delete(ENV.fetch("NETWORK_MARKER")) if File.exist?(ENV.fetch("NETWORK_MARKER"))
                  puts('{"status":"CLEANED"}')
                else
                  exit 64
                end
                """, StandardCharsets.UTF_8);
        Map<String, String> environment = Map.of(
                "COMMAND_LOG", commandLog.toString(),
                "ATTEMPTS", attempts.toString(),
                "CONTAINER_MARKER", container.toString(),
                "NETWORK_MARKER", network.toString(),
                "PID_FILE", pids.toString()
        );
        String runId = "mysql-cleanup01";
        try {
            assertThatThrownBy(() -> Phase01ServiceHarness.startServiceForTest(
                    script, "mysql", runId, environment, 250, 4_096))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("PHASE01_SERVICE_COMMAND_TIMEOUT");
            assertThat(Files.readString(attempts).trim()).isEqualTo("2");
            assertThat(Files.exists(container)).isFalse();
            assertThat(Files.exists(network)).isFalse();
            assertThat(Files.readAllLines(commandLog)).containsExactly(
                    "start --service mysql --run-id " + runId,
                    "stop --service mysql --run-id " + runId,
                    "stop --service mysql --run-id " + runId
            );
            assertPublishedProcessesGone(pids);
        } finally {
            try (Stream<Path> children = Files.list(directory)) {
                children.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Test cleanup only.
                    }
                });
            }
            Files.deleteIfExists(directory);
        }
    }

    private static String ignoringTreeSource() {
        return """
                trap("TERM") { }
                child = fork do
                  trap("TERM") { }
                  loop { sleep 1 }
                end
                File.write(ARGV.fetch(0), [Process.pid, child].join(","))
                Process.wait(child)
                """;
    }

    private static void assertPublishedProcessesGone(Path pids) throws Exception {
        long publishDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!Files.isRegularFile(pids) && System.nanoTime() < publishDeadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertThat(Files.isRegularFile(pids)).isTrue();
        for (String value : Files.readString(pids).trim().split(",")) {
            long pid = Long.parseLong(value);
            long exitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
                    && System.nanoTime() < exitDeadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                    .as("process %s survived cleanup", pid).isFalse();
        }
    }
}
