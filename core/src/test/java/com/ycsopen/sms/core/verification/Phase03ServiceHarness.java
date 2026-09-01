package com.ycsopen.sms.core.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Fixed-argument, bounded bridge to the real Phase 3 service fixtures. */
final class Phase03ServiceHarness {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Path ROOT = locateRepositoryRoot();
    private static final Path SCRIPT = ROOT.resolve("scripts/lib/phase-03/service_checks.rb");
    private static final int OUTPUT_LIMIT = 1_048_576;
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(12);

    private Phase03ServiceHarness() {
    }

    static FixtureSet startAll() {
        ServiceSession mysql = null;
        ServiceSession minio = null;
        ServiceSession softHsm = null;
        try {
            mysql = startMySql();
            minio = startMinio();
            softHsm = startSoftHsm();
            return new FixtureSet(mysql, minio, softHsm);
        } catch (RuntimeException failure) {
            closeQuietly(softHsm);
            closeQuietly(minio);
            closeQuietly(mysql);
            throw failure;
        }
    }

    private static ServiceSession startMySql() {
        String runId = "mysql-" + randomHex(6);
        String username = "phase01_" + randomHex(4);
        String password = randomHex(24);
        String rootPassword = randomHex(24);
        try {
            JsonNode identity = start("mysql", runId, Map.of(
                    "PHASE03_MYSQL_USER", username,
                    "PHASE03_MYSQL_PASSWORD", password,
                    "PHASE03_MYSQL_ROOT_PASSWORD", rootPassword));
            assertIdentity(identity, "mysql", runId);
            if (!identity.path("image_reference").asText().equals(
                    "mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb")) {
                throw new FixtureException("MYSQL_IMAGE_IDENTITY_MISMATCH");
            }
            return ServiceSession.mysql(runId, identity, username, password, rootPassword);
        } catch (RuntimeException failure) {
            cleanupAfterFailedStart("mysql", runId, failure);
            throw failure;
        }
    }

    private static ServiceSession startMinio() {
        String runId = "minio-" + randomHex(6);
        String accessKey = "phase03" + randomHex(6);
        String secretKey = randomHex(24);
        try {
            JsonNode identity = start("minio", runId, Map.of(
                    "PHASE03_MINIO_ACCESS_KEY", accessKey,
                    "PHASE03_MINIO_SECRET_KEY", secretKey));
            assertIdentity(identity, "minio", runId);
            if (!identity.path("image_reference").asText().equals(
                    "minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e")) {
                throw new FixtureException("MINIO_IMAGE_IDENTITY_MISMATCH");
            }
            return ServiceSession.minio(runId, identity, accessKey, secretKey);
        } catch (RuntimeException failure) {
            cleanupAfterFailedStart("minio", runId, failure);
            throw failure;
        }
    }

    private static ServiceSession startSoftHsm() {
        String runId = "softhsm-" + randomHex(6);
        try {
            JsonNode identity = start("softhsm", runId, Map.of());
            assertIdentity(identity, "softhsm", runId);
            if (!identity.path("version").asText().equals("2.7.0")
                    || identity.path("mechanism_count").asInt() != 4
                    || identity.path("nonextractable_key_count").asInt() != 2) {
                throw new FixtureException("SOFTHSM_PREFLIGHT_INCOMPLETE");
            }
            Path destination = ROOT.resolve("core/target/phase03/services").resolve(runId).normalize();
            SoftHsmHandoff handoff = readHandoff(destination);
            return ServiceSession.softHsm(runId, identity, handoff);
        } catch (RuntimeException failure) {
            cleanupAfterFailedStart("softhsm", runId, failure);
            throw failure;
        }
    }

    private static void cleanupAfterFailedStart(String service, String runId, RuntimeException failure) {
        try {
            stop(service, runId);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static JsonNode start(String service, String runId, Map<String, String> environment) {
        return runJson(List.of("/usr/bin/env", "ruby", SCRIPT.toString(), "start",
                "--service", service, "--run-id", runId), environment);
    }

    private static void stop(String service, String runId) {
        JsonNode result = runJson(List.of("/usr/bin/env", "ruby", SCRIPT.toString(), "stop",
                "--service", service, "--run-id", runId), Map.of());
        if (!result.path("status").asText().equals("CLEANED")) {
            throw new FixtureException("SERVICE_CLEANUP_FAILED");
        }
    }

    static CommandResult runChecked(List<String> argv, Map<String, String> environment) {
        CommandResult result = execute(argv, environment);
        if (result.exitCode() != 0) {
            throw new FixtureException("SERVICE_COMMAND_FAILED: " + sanitize(result.stderr()));
        }
        return result;
    }

    private static JsonNode runJson(List<String> argv, Map<String, String> environment) {
        CommandResult result = execute(argv, environment);
        try {
            if (result.exitCode() != 0) {
                JsonNode error = JSON.readTree(result.stderr());
                throw new FixtureException(error.path("status").asText("FAIL") + ":"
                        + error.path("error_id").asText("SERVICE_FAILURE") + ":"
                        + sanitize(error.path("diagnostic").asText()));
            }
            JsonNode value = JSON.readTree(result.stdout());
            if (value == null || !value.isObject()) {
                throw new FixtureException("SERVICE_OUTPUT_MALFORMED");
            }
            return value;
        } catch (IOException malformed) {
            throw new FixtureException("SERVICE_OUTPUT_MALFORMED", malformed);
        }
    }

    private static CommandResult execute(List<String> argv, Map<String, String> environment) {
        Process process = null;
        try (ExecutorService drains = Executors.newFixedThreadPool(2)) {
            ProcessBuilder builder = new ProcessBuilder(argv);
            builder.directory(ROOT.toFile());
            builder.environment().putAll(environment);
            process = builder.start();
            Process started = process;
            Future<BoundedOutput> stdout = drains.submit(() -> readBounded(started.getInputStream()));
            Future<BoundedOutput> stderr = drains.submit(() -> readBounded(started.getErrorStream()));
            if (!process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw new FixtureException("SERVICE_COMMAND_TIMEOUT");
            }
            BoundedOutput out = stdout.get(5, TimeUnit.SECONDS);
            BoundedOutput err = stderr.get(5, TimeUnit.SECONDS);
            if (out.overflow() || err.overflow()) {
                throw new FixtureException("SERVICE_OUTPUT_LIMIT");
            }
            return new CommandResult(process.exitValue(), out.value(), err.value());
        } catch (FixtureException failure) {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            throw failure;
        } catch (Exception failure) {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            throw new FixtureException("SERVICE_PROCESS_FAILURE", failure);
        }
    }

    private static BoundedOutput readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        boolean overflow = false;
        try (input) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                int remaining = OUTPUT_LIMIT - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(remaining, count));
                }
                overflow |= count > remaining;
            }
        }
        return new BoundedOutput(output.toString(StandardCharsets.UTF_8), overflow);
    }

    private static void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    static SoftHsmHandoff readHandoff(Path destination) {
        try {
            Path ownedRoot = ROOT.resolve("core/target/phase03/services").toRealPath();
            Path realDestination = destination.toRealPath();
            if (!realDestination.startsWith(ownedRoot) || Files.isSymbolicLink(destination)) {
                throw new FixtureException("SOFTHSM_HANDOFF_PATH_INVALID");
            }
            Path handoffPath = realDestination.resolve("runtime/handoff.json");
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(handoffPath);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw new FixtureException("SOFTHSM_HANDOFF_PERMISSION_INVALID");
            }
            JsonNode handoff = JSON.readTree(Files.readAllBytes(handoffPath));
            Set<String> expectedFields = Set.of(
                    "schema_version", "version", "source_sha256", "capability_patch_sha256",
                    "cli", "library", "header",
                    "config", "pin_source", "slot", "token_dir");
            Set<String> actualFields = new java.util.HashSet<>();
            handoff.fieldNames().forEachRemaining(actualFields::add);
            if (!actualFields.equals(expectedFields)
                    || !handoff.path("schema_version").asText().equals("ycs-softhsm-handoff/v1")
                    || !handoff.path("version").asText().equals("2.7.0")
                    || !handoff.path("source_sha256").asText().equals(
                    "be14a5820ec457eac5154462ffae51ba5d8a643f6760514d4b4b83a77be91573")
                    || !handoff.path("capability_patch_sha256").asText().equals(
                    "61f77b1f78ecb94b55da8decb5041d5a40e661c3034eed59f6c2f44645cb3efd")
                    || handoff.path("slot").asLong(-1) < 0) {
                throw new FixtureException("SOFTHSM_HANDOFF_IDENTITY_INVALID");
            }
            Path cli = containedRegularFile(realDestination, handoff.path("cli").asText());
            Path library = containedRegularFile(realDestination, handoff.path("library").asText());
            Path config = containedRegularFile(realDestination, handoff.path("config").asText());
            Path pinSource = containedRegularFile(realDestination, handoff.path("pin_source").asText());
            assertPrivateFile(config);
            assertPrivateFile(pinSource);
            List<String> pins = Files.readAllLines(pinSource, StandardCharsets.UTF_8);
            if (pins.size() != 2 || pins.get(0).length() < 16 || pins.get(1).length() < 16) {
                throw new FixtureException("SOFTHSM_PIN_SOURCE_INVALID");
            }
            return new SoftHsmHandoff(cli, library, config, pinSource,
                    handoff.path("slot").asLong(-1), pins.get(1).toCharArray());
        } catch (IOException failure) {
            throw new FixtureException("SOFTHSM_HANDOFF_READ_FAILED", failure);
        }
    }

    private static void assertPrivateFile(Path path) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
        if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                || permission.name().startsWith("OTHERS_"))) {
            throw new FixtureException("SOFTHSM_PRIVATE_FILE_PERMISSION_INVALID");
        }
    }

    private static Path containedRegularFile(Path destination, String rawPath) throws IOException {
        Path path = Path.of(rawPath);
        if (Files.isSymbolicLink(path)) {
            throw new FixtureException("SOFTHSM_HANDOFF_PATH_INVALID");
        }
        Path real = path.toRealPath();
        if (!real.startsWith(destination) || !Files.isRegularFile(real)) {
            throw new FixtureException("SOFTHSM_HANDOFF_PATH_INVALID");
        }
        return real;
    }

    private static void assertIdentity(JsonNode identity, String service, String runId) {
        if (!identity.path("status").asText().equals("READY")
                || !identity.path("service").asText().equals(service)
                || !identity.path("run_id").asText().equals(runId)) {
            throw new FixtureException("SERVICE_IDENTITY_MISMATCH");
        }
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("scripts/lib/phase-03/service_checks.rb"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new FixtureException("REPOSITORY_ROOT_NOT_FOUND");
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static String sanitize(String value) {
        return value.replaceAll("(?i)(password|secret|pin)\\s*[=:]\\s*\\S+", "[REDACTED]")
                .replaceAll("(?:/[^\\s]+)+", "[PATH_REDACTED]");
    }

    private static void closeQuietly(ServiceSession session) {
        if (session != null) {
            try {
                session.close();
            } catch (RuntimeException ignored) {
                // The original startup failure is primary; assert-clean catches residue.
            }
        }
    }

    record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private record BoundedOutput(String value, boolean overflow) {
    }

    record SoftHsmHandoff(Path cli, Path library, Path config, Path pinSource, long slot, char[] userPin) {
    }

    static final class FixtureSet implements AutoCloseable {
        private final ServiceSession mysql;
        private final ServiceSession minio;
        private final ServiceSession softHsm;
        private boolean closed;

        private FixtureSet(ServiceSession mysql, ServiceSession minio, ServiceSession softHsm) {
            this.mysql = mysql;
            this.minio = minio;
            this.softHsm = softHsm;
        }

        ServiceSession mysql() {
            return mysql;
        }

        ServiceSession minio() {
            return minio;
        }

        ServiceSession softHsm() {
            return softHsm;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                RuntimeException failure = null;
                for (ServiceSession session : List.of(softHsm, minio, mysql)) {
                    try {
                        session.close();
                    } catch (RuntimeException cleanupFailure) {
                        if (failure == null) {
                            failure = cleanupFailure;
                        } else {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                }
                closed = true;
                if (failure != null) {
                    throw failure;
                }
            }
        }
    }

    static final class ServiceSession implements AutoCloseable {
        private final String service;
        private final String runId;
        private final String host;
        private final int port;
        private final String containerName;
        private final String username;
        private final String password;
        private final char[] rootPassword;
        private final SoftHsmHandoff softHsm;
        private boolean closed;

        private ServiceSession(String service, String runId, JsonNode identity, String username,
                               String password, char[] rootPassword, SoftHsmHandoff softHsm) {
            this.service = service;
            this.runId = runId;
            this.host = identity.path("host").asText();
            this.port = identity.path("port").asInt();
            this.containerName = identity.path("container_name").asText();
            this.username = username;
            this.password = password;
            this.rootPassword = rootPassword == null ? null : rootPassword.clone();
            this.softHsm = softHsm;
        }

        static ServiceSession mysql(String runId, JsonNode identity, String username,
                                    String password, String rootPassword) {
            return new ServiceSession("mysql", runId, identity, username, password,
                    rootPassword.toCharArray(), null);
        }

        static ServiceSession minio(String runId, JsonNode identity, String username, String password) {
            return new ServiceSession("minio", runId, identity, username, password, null, null);
        }

        static ServiceSession softHsm(String runId, JsonNode identity, SoftHsmHandoff handoff) {
            return new ServiceSession("softhsm", runId, identity, null, null, null, handoff);
        }

        String host() {
            return host;
        }

        int port() {
            return port;
        }

        String containerName() {
            if (containerName == null || containerName.isBlank()) {
                throw new FixtureException("MYSQL_CONTAINER_IDENTITY_UNAVAILABLE");
            }
            return containerName;
        }

        String username() {
            return username;
        }

        String password() {
            return password;
        }

        char[] rootPassword() {
            if (rootPassword == null) {
                throw new FixtureException("MYSQL_ROOT_CREDENTIAL_UNAVAILABLE");
            }
            return rootPassword.clone();
        }

        SoftHsmHandoff softHsm() {
            return softHsm;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                try {
                    stop(service, runId);
                } finally {
                    if (softHsm != null) {
                        java.util.Arrays.fill(softHsm.userPin(), '\0');
                    }
                    if (rootPassword != null) {
                        java.util.Arrays.fill(rootPassword, '\0');
                    }
                    closed = true;
                }
            }
        }
    }

    static final class FixtureException extends RuntimeException {
        FixtureException(String message) {
            super(message);
        }

        FixtureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
