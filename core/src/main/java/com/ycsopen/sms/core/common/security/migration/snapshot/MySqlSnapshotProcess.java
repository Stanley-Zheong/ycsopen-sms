package com.ycsopen.sms.core.common.security.migration.snapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Process boundary whose production implementation owns the complete MySQL client argv. */
public interface MySqlSnapshotProcess {

    DumpSession startDump(Database source);

    RestoreSession startRestore(Database target);

    interface DumpSession extends AutoCloseable {
        InputStream stdout();

        void awaitSuccess();

        @Override
        void close();
    }

    interface RestoreSession extends AutoCloseable {
        OutputStream stdin();

        void awaitSuccess();

        @Override
        void close();
    }

    record Database(String host, int port, String username, char[] password, String schema) {
        private static final Pattern HOST = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,252}");
        private static final Pattern USER = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");
        private static final Pattern SCHEMA = Pattern.compile("[A-Za-z0-9_]{1,64}");

        public Database {
            if (host == null || !HOST.matcher(host).matches()
                    || port < 1 || port > 65_535
                    || username == null || !USER.matcher(username).matches()
                    || password == null || password.length < 1 || password.length > 1_024
                    || schema == null || !SCHEMA.matcher(schema).matches()) {
                throw SnapshotManifest.invalid();
            }
            password = password.clone();
        }

        @Override
        public char[] password() {
            return password.clone();
        }
    }

    /** No-shell process implementation; callers can configure executables but never client flags. */
    final class FixedArgumentClient implements MySqlSnapshotProcess {
        private static final int STDERR_LIMIT = 65_536;
        private static final long MAXIMUM_EXECUTABLE_BYTES = 536_870_912L;
        private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(15);
        private static final Duration AUTHORITY_PROBE_TIMEOUT = Duration.ofSeconds(2);
        private static final int AUTHORITY_PROBE_OUTPUT_LIMIT = 8_192;
        private static final Path ID_EXECUTABLE = Path.of("/usr/bin/id");
        private static final Path MACOS_LS_EXECUTABLE = Path.of("/bin/ls");
        private static final Path LINUX_LS_EXECUTABLE = Path.of("/usr/bin/ls");
        private static final Set<Path> TRUSTED_EXECUTABLE_ROOTS = Set.of(
                Path.of("/usr/bin"), Path.of("/opt/ycsopen/mysql-client"));
        private static final Pattern PATH_COMPONENT =
                Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
        private static final Pattern SHA256 = Pattern.compile("sha256:[a-f0-9]{64}");
        private final ExecutableIdentity mysqldumpIdentity;
        private final ExecutableIdentity mysqlIdentity;
        private final PasswordReader passwordReader;
        private final ProcessStarter processStarter;

        public FixedArgumentClient(
                Path mysqldump,
                String mysqldumpSha256,
                Path mysql,
                String mysqlSha256) {
            this(mysqldump, mysqldumpSha256, mysql, mysqlSha256,
                    TrustedExecutablePolicy.system(), Database::password, ProcessBuilder::start);
        }

        FixedArgumentClient(
                Path mysqldump,
                String mysqldumpSha256,
                Path mysql,
                String mysqlSha256,
                TrustedExecutablePolicy executablePolicy,
                PasswordReader passwordReader,
                ProcessStarter processStarter) {
            TrustedExecutablePolicy policy = Objects.requireNonNull(
                    executablePolicy, "executablePolicy");
            this.mysqldumpIdentity = ExecutableIdentity.pin(
                    mysqldump, mysqldumpSha256, policy);
            this.mysqlIdentity = ExecutableIdentity.pin(mysql, mysqlSha256, policy);
            this.passwordReader = Objects.requireNonNull(passwordReader, "passwordReader");
            this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        }

        @Override
        public DumpSession startDump(Database source) {
            Objects.requireNonNull(source, "source");
            List<String> argv = new ArrayList<>();
            Path current = mysqldumpIdentity.requireCurrent();
            argv.add(current.toString());
            argv.add("--no-defaults");
            argv.add("--protocol=TCP");
            argv.add("--host=" + source.host());
            argv.add("--port=" + source.port());
            argv.add("--user=" + source.username());
            argv.add("--single-transaction");
            argv.add("--skip-lock-tables");
            argv.add("--quick");
            argv.add("--skip-opt");
            argv.add("--create-options");
            argv.add("--set-charset");
            argv.add("--quote-names");
            argv.add("--triggers");
            argv.add("--skip-add-drop-table");
            argv.add("--skip-add-locks");
            argv.add("--skip-disable-keys");
            argv.add("--complete-insert");
            argv.add("--skip-extended-insert");
            argv.add("--order-by-primary");
            argv.add("--hex-blob");
            argv.add("--default-character-set=utf8mb4");
            argv.add("--tz-utc");
            argv.add("--set-gtid-purged=OFF");
            argv.add("--no-tablespaces");
            argv.add("--column-statistics=0");
            argv.add("--skip-comments");
            argv.add("--skip-dump-date");
            argv.add(source.schema());
            RunningProcess process = RunningProcess.start(
                    argv, passwordReader.read(source), processStarter);
            return new DumpSession() {
                @Override
                public InputStream stdout() {
                    return process.stdout();
                }

                @Override
                public void awaitSuccess() {
                    process.awaitSuccess();
                }

                @Override
                public void close() {
                    process.close();
                }
            };
        }

        @Override
        public RestoreSession startRestore(Database target) {
            Objects.requireNonNull(target, "target");
            List<String> argv = new ArrayList<>();
            Path current = mysqlIdentity.requireCurrent();
            argv.addAll(List.of(
                    current.toString(),
                    "--no-defaults", "--protocol=TCP", "--host=" + target.host(),
                    "--port=" + target.port(), "--user=" + target.username(),
                    "--binary-mode", "--default-character-set=utf8mb4",
                    "--database=" + target.schema()));
            RunningProcess process = RunningProcess.start(
                    argv, passwordReader.read(target), processStarter);
            return new RestoreSession() {
                @Override
                public OutputStream stdin() {
                    return process.stdin();
                }

                @Override
                public void awaitSuccess() {
                    process.closeStdin();
                    process.awaitSuccess();
                }

                @Override
                public void close() {
                    process.close();
                }
            };
        }

        private record ExecutableIdentity(
                Path path, String sha256, Object fileKey, long size,
                TrustedExecutablePolicy policy) {

            private static ExecutableIdentity pin(
                    Path candidate, String expectedSha256, TrustedExecutablePolicy policy) {
                VerifiedExecutable verified = policy.verify(candidate, expectedSha256);
                return new ExecutableIdentity(
                        verified.path(), expectedSha256, verified.fileKey(), verified.size(), policy);
            }

            private Path requireCurrent() {
                VerifiedExecutable current = policy.verify(path, sha256);
                if (!path.equals(current.path()) || !fileKey.equals(current.fileKey())
                        || size != current.size()) {
                    throw SnapshotManifest.invalid();
                }
                return current.path();
            }
        }

        static final class TrustedExecutablePolicy {
            private static final int GROUP_OR_OTHER_WRITE = 0022;
            private final FileMetadataReader metadataReader;
            private final RuntimeIdentityReader runtimeIdentityReader;
            private final Set<Path> trustedRoots;

            TrustedExecutablePolicy(
                    FileMetadataReader metadataReader,
                    RuntimeIdentityReader runtimeIdentityReader,
                    Set<Path> trustedRoots) {
                this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader");
                this.runtimeIdentityReader = Objects.requireNonNull(
                        runtimeIdentityReader, "runtimeIdentityReader");
                this.trustedRoots = Set.copyOf(Objects.requireNonNull(trustedRoots, "trustedRoots"));
            }

            static TrustedExecutablePolicy system() {
                FixedOsCommandRunner commands = new FixedOsCommandRunner(ProcessBuilder::start);
                SupportedOperatingSystem operatingSystem =
                        SupportedOperatingSystem.current(System.getProperty("os.name"));
                return new TrustedExecutablePolicy(
                        new SystemFileMetadataReader(
                                new OsAclVerifier(commands, operatingSystem)),
                        new UnixUserIdentityReader(commands)::read,
                        TRUSTED_EXECUTABLE_ROOTS);
            }

            static TrustedExecutablePolicy system(RuntimeIdentityReader runtimeIdentityReader) {
                FixedOsCommandRunner commands = new FixedOsCommandRunner(ProcessBuilder::start);
                SupportedOperatingSystem operatingSystem =
                        SupportedOperatingSystem.current(System.getProperty("os.name"));
                return new TrustedExecutablePolicy(
                        new SystemFileMetadataReader(
                                new OsAclVerifier(commands, operatingSystem)),
                        runtimeIdentityReader,
                        TRUSTED_EXECUTABLE_ROOTS);
            }

            VerifiedExecutable verify(Path candidate, String expectedSha256) {
                RuntimeIdentity identity = runtimeIdentityReader.read();
                if (identity.effectiveUserId() <= 0 || identity.realUserId() <= 0
                        || identity.effectiveUserId() != identity.realUserId()
                        || expectedSha256 == null || !SHA256.matcher(expectedSha256).matches()) {
                    throw SnapshotManifest.invalid();
                }
                Objects.requireNonNull(candidate, "candidate");
                if (!candidate.isAbsolute() || !candidate.equals(candidate.normalize())) {
                    throw SnapshotManifest.invalid();
                }
                Path requested = candidate.normalize();
                for (Path component : requested) {
                    if (!PATH_COMPONENT.matcher(component.toString()).matches()) {
                        throw SnapshotManifest.invalid();
                    }
                }
                boolean trusted = trustedRoots.stream().anyMatch(root ->
                        requested.startsWith(root) && !requested.equals(root));
                if (!trusted) {
                    throw SnapshotManifest.invalid();
                }
                Path current = requested.getRoot();
                if (current == null) {
                    throw SnapshotManifest.invalid();
                }
                validateNode(current, metadataReader.read(current), false);
                for (Path component : requested) {
                    current = current.resolve(component);
                    validateNode(current, metadataReader.read(current), current.equals(requested));
                }
                PathMetadata before = metadataReader.read(requested);
                validateNode(requested, before, true);
                if (before.fileKey() == null || before.size() < 1
                        || before.size() > MAXIMUM_EXECUTABLE_BYTES
                        || !expectedSha256.equals(metadataReader.sha256(
                        requested, before.size()))) {
                    throw SnapshotManifest.invalid();
                }
                PathMetadata after = metadataReader.read(requested);
                validateNode(requested, after, true);
                if (!before.fileKey().equals(after.fileKey()) || before.size() != after.size()) {
                    throw SnapshotManifest.invalid();
                }
                return new VerifiedExecutable(requested, after.fileKey(), after.size());
            }

            private static void validateNode(
                    Path expected, PathMetadata metadata, boolean leaf) {
                if (!expected.equals(metadata.canonicalPath()) || metadata.symbolicLink()
                        || metadata.ownerUserId() != 0 || metadata.ownerGroupId() != 0
                        || (metadata.unixMode() & GROUP_OR_OTHER_WRITE) != 0
                        || metadata.untrustedAclWriteGrant()) {
                    throw SnapshotManifest.invalid();
                }
                if (leaf) {
                    if (!metadata.regularFile() || !metadata.executable()) {
                        throw SnapshotManifest.invalid();
                    }
                } else if (!metadata.directory()) {
                    throw SnapshotManifest.invalid();
                }
            }
        }

        private static final class SystemFileMetadataReader implements FileMetadataReader {
            private final OsAclVerifier aclVerifier;

            private SystemFileMetadataReader(OsAclVerifier aclVerifier) {
                this.aclVerifier = Objects.requireNonNull(aclVerifier, "aclVerifier");
            }

            @Override
            public PathMetadata read(Path path) {
                try {
                    if (Files.isSymbolicLink(path)) {
                        throw SnapshotManifest.invalid();
                    }
                    FileStore store = Files.getFileStore(path);
                    if (!store.supportsFileAttributeView(PosixFileAttributeView.class)) {
                        throw SnapshotManifest.invalid();
                    }
                    BasicFileAttributes basic = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    long uid = ((Number) Files.getAttribute(
                            path, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue();
                    long gid = ((Number) Files.getAttribute(
                            path, "unix:gid", LinkOption.NOFOLLOW_LINKS)).longValue();
                    int mode = ((Number) Files.getAttribute(
                            path, "unix:mode", LinkOption.NOFOLLOW_LINKS)).intValue();
                    aclVerifier.requireNoAcl(path);
                    return new PathMetadata(
                            path.toRealPath(LinkOption.NOFOLLOW_LINKS), basic.isSymbolicLink(),
                            basic.isDirectory(), basic.isRegularFile(), Files.isExecutable(path),
                            uid, gid, mode, basic.size(), basic.fileKey(),
                            false);
                } catch (IOException | UnsupportedOperationException | ClassCastException failure) {
                    throw SnapshotManifest.invalid();
                }
            }

            @Override
            public String sha256(Path path, long expectedSize) {
                MessageDigest digest = sha256Digest();
                long total = 0;
                byte[] buffer = new byte[64 * 1_024];
                try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total = Math.addExact(total, read);
                        if (total > expectedSize || total > MAXIMUM_EXECUTABLE_BYTES) {
                            throw SnapshotManifest.invalid();
                        }
                        digest.update(buffer, 0, read);
                    }
                    if (total != expectedSize) {
                        throw SnapshotManifest.invalid();
                    }
                    return "sha256:" + HexFormat.of().formatHex(digest.digest());
                } catch (IOException | ArithmeticException failure) {
                    throw SnapshotManifest.invalid();
                } finally {
                    Arrays.fill(buffer, (byte) 0);
                }
            }

        }

        static final class UnixUserIdentityReader {
            private static final Pattern UID =
                    Pattern.compile("(?:0|[1-9][0-9]{0,18})\\n");
            private final OsCommandRunner commands;

            UnixUserIdentityReader(OsCommandRunner commands) {
                this.commands = Objects.requireNonNull(commands, "commands");
            }

            RuntimeIdentity read() {
                return new RuntimeIdentity(
                        readUid(List.of(ID_EXECUTABLE.toString(), "-u")),
                        readUid(List.of(ID_EXECUTABLE.toString(), "-ru")));
            }

            private long readUid(List<String> argv) {
                String output = successfulAscii(commands.run(argv));
                if (!UID.matcher(output).matches()) {
                    throw SnapshotManifest.invalid();
                }
                try {
                    long uid = Long.parseLong(output.substring(0, output.length() - 1));
                    if (uid < 0) {
                        throw SnapshotManifest.invalid();
                    }
                    return uid;
                } catch (NumberFormatException failure) {
                    throw SnapshotManifest.invalid();
                }
            }
        }

        static final class OsAclVerifier {
            private static final Pattern MACOS_PERMISSIONS =
                    Pattern.compile("[bcdlps-][rwxStTs-]{9}@?");
            private static final Pattern LINUX_PERMISSIONS =
                    Pattern.compile("[bcdlps-][rwxStTs-]{9}\\.?");
            private final OsCommandRunner commands;
            private final SupportedOperatingSystem operatingSystem;

            OsAclVerifier(
                    OsCommandRunner commands, SupportedOperatingSystem operatingSystem) {
                this.commands = Objects.requireNonNull(commands, "commands");
                this.operatingSystem = Objects.requireNonNull(
                        operatingSystem, "operatingSystem");
            }

            void requireNoAcl(Path path) {
                Objects.requireNonNull(path, "path");
                String flag = operatingSystem == SupportedOperatingSystem.MACOS
                        ? "-lde" : "-ldn";
                Path executable = operatingSystem == SupportedOperatingSystem.MACOS
                        ? MACOS_LS_EXECUTABLE : LINUX_LS_EXECUTABLE;
                String output = successfulAscii(commands.run(List.of(
                        executable.toString(), flag, "--", path.toString())));
                if (!output.endsWith("\n")
                        || output.indexOf('\n') != output.length() - 1) {
                    throw SnapshotManifest.invalid();
                }
                String line = output.substring(0, output.length() - 1);
                int separator = line.indexOf(' ');
                if (separator < 0 || !line.endsWith(" " + path)) {
                    throw SnapshotManifest.invalid();
                }
                String permissions = line.substring(0, separator);
                Pattern accepted = operatingSystem == SupportedOperatingSystem.MACOS
                        ? MACOS_PERMISSIONS : LINUX_PERMISSIONS;
                if (!accepted.matcher(permissions).matches()) {
                    throw SnapshotManifest.invalid();
                }
            }
        }

        enum SupportedOperatingSystem {
            MACOS,
            LINUX;

            static SupportedOperatingSystem current(String name) {
                if (name == null) {
                    throw SnapshotManifest.invalid();
                }
                String normalized = name.toLowerCase(Locale.ROOT);
                if (normalized.startsWith("mac os")) {
                    return MACOS;
                }
                if (normalized.equals("linux")) {
                    return LINUX;
                }
                throw SnapshotManifest.invalid();
            }
        }

        @FunctionalInterface
        interface OsCommandRunner {
            OsCommandResult run(List<String> argv);
        }

        record OsCommandResult(
                int exitCode,
                byte[] stdout,
                byte[] stderr,
                boolean timedOut,
                boolean stdoutOverflow,
                boolean stderrOverflow) {
            OsCommandResult {
                stdout = stdout == null ? new byte[0] : stdout.clone();
                stderr = stderr == null ? new byte[0] : stderr.clone();
            }

            @Override
            public byte[] stdout() {
                return stdout.clone();
            }

            @Override
            public byte[] stderr() {
                return stderr.clone();
            }
        }

        static final class FixedOsCommandRunner implements OsCommandRunner {
            private final ProcessStarter processStarter;

            FixedOsCommandRunner(ProcessStarter processStarter) {
                this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
            }

            @Override
            public OsCommandResult run(List<String> argv) {
                requireFixedCommand(argv);
                ProcessBuilder builder = new ProcessBuilder(argv);
                builder.environment().clear();
                builder.environment().put("LC_ALL", "C");
                builder.environment().put("LANG", "C");
                try {
                    Process process = processStarter.start(builder);
                    builder.environment().clear();
                    ProbeOutput stdout = new ProbeOutput(process.getInputStream());
                    ProbeOutput stderr = new ProbeOutput(process.getErrorStream());
                    Thread stdoutThread = Thread.ofVirtual()
                            .name("mysql-authority-stdout").start(stdout);
                    Thread stderrThread = Thread.ofVirtual()
                            .name("mysql-authority-stderr").start(stderr);
                    if (!process.waitFor(
                            AUTHORITY_PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        terminateProbe(process);
                        joinProbe(stdoutThread);
                        joinProbe(stderrThread);
                        if (stdoutThread.isAlive() || stderrThread.isAlive()) {
                            return new OsCommandResult(
                                    -1, new byte[0], new byte[0], true, true, true);
                        }
                        return new OsCommandResult(
                                -1, stdout.bytes(), stderr.bytes(), true,
                                stdout.overflow(), stderr.overflow());
                    }
                    joinProbe(stdoutThread);
                    joinProbe(stderrThread);
                    if (stdoutThread.isAlive() || stderrThread.isAlive()) {
                        terminateProbe(process);
                        throw SnapshotManifest.invalid();
                    }
                    return new OsCommandResult(
                            process.exitValue(), stdout.bytes(), stderr.bytes(), false,
                            stdout.overflow(), stderr.overflow());
                } catch (IOException failure) {
                    throw SnapshotManifest.invalid();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw SnapshotManifest.invalid();
                } finally {
                    builder.environment().clear();
                }
            }

            private static void requireFixedCommand(List<String> argv) {
                if (argv.equals(List.of(ID_EXECUTABLE.toString(), "-u"))
                        || argv.equals(List.of(ID_EXECUTABLE.toString(), "-ru"))) {
                    return;
                }
                if (argv.size() == 4
                        && (MACOS_LS_EXECUTABLE.toString().equals(argv.get(0))
                        || LINUX_LS_EXECUTABLE.toString().equals(argv.get(0)))
                        && ("-lde".equals(argv.get(1)) || "-ldn".equals(argv.get(1)))
                        && "--".equals(argv.get(2))
                        && Path.of(argv.get(3)).isAbsolute()) {
                    return;
                }
                throw SnapshotManifest.invalid();
            }

            private static void terminateProbe(Process process) {
                process.destroy();
                try {
                    if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(250, TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }

            private static void joinProbe(Thread thread) throws InterruptedException {
                thread.join(Duration.ofSeconds(1));
            }
        }

        private static String successfulAscii(OsCommandResult result) {
            if (result == null || result.timedOut() || result.exitCode() != 0
                    || result.stdoutOverflow() || result.stderrOverflow()
                    || result.stderr().length != 0) {
                throw SnapshotManifest.invalid();
            }
            byte[] bytes = result.stdout();
            for (byte value : bytes) {
                int unsigned = Byte.toUnsignedInt(value);
                if (unsigned != '\n' && (unsigned < 0x20 || unsigned > 0x7e)) {
                    throw SnapshotManifest.invalid();
                }
            }
            return new String(bytes, StandardCharsets.US_ASCII);
        }

        private static final class ProbeOutput implements Runnable {
            private final InputStream input;
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            private volatile boolean overflow;

            private ProbeOutput(InputStream input) {
                this.input = input;
            }

            @Override
            public void run() {
                try (input) {
                    byte[] buffer = new byte[512];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        int remaining = AUTHORITY_PROBE_OUTPUT_LIMIT - bytes.size();
                        if (remaining > 0) {
                            bytes.write(buffer, 0, Math.min(remaining, read));
                        }
                        overflow |= read > Math.max(remaining, 0);
                    }
                } catch (IOException failure) {
                    overflow = true;
                }
            }

            private synchronized byte[] bytes() {
                return bytes.toByteArray();
            }

            private boolean overflow() {
                return overflow;
            }
        }

        private static MessageDigest sha256Digest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("Java 21 must provide SHA-256", impossible);
            }
        }

        record VerifiedExecutable(Path path, Object fileKey, long size) {
        }

        record PathMetadata(
                Path canonicalPath,
                boolean symbolicLink,
                boolean directory,
                boolean regularFile,
                boolean executable,
                long ownerUserId,
                long ownerGroupId,
                int unixMode,
                long size,
                Object fileKey,
                boolean untrustedAclWriteGrant) {
        }

        interface FileMetadataReader {
            PathMetadata read(Path path);

            String sha256(Path path, long expectedSize);
        }

        record RuntimeIdentity(long effectiveUserId, long realUserId) {
        }

        @FunctionalInterface
        interface RuntimeIdentityReader {
            RuntimeIdentity read();
        }

        @FunctionalInterface
        interface PasswordReader {
            char[] read(Database database);
        }

        @FunctionalInterface
        interface ProcessStarter {
            Process start(ProcessBuilder builder) throws IOException;
        }

        private static final class RunningProcess implements AutoCloseable {
            private final Process process;
            private final Thread stderrDrain;
            private final BoundedError stderr;
            private boolean waited;

            private RunningProcess(Process process) {
                this.process = process;
                this.stderr = new BoundedError(process.getErrorStream());
                this.stderrDrain = Thread.ofVirtual().name("mysql-snapshot-stderr").start(stderr);
            }

            static RunningProcess start(
                    List<String> argv, char[] password, ProcessStarter processStarter) {
                ProcessBuilder builder = new ProcessBuilder(argv);
                String credential = new String(password);
                Arrays.fill(password, '\0');
                try {
                    builder.environment().clear();
                    builder.environment().put("MYSQL_PWD", credential);
                    builder.environment().put("LC_ALL", "C");
                    builder.environment().put("LANG", "C");
                    builder.environment().put("TZ", "UTC");
                    Process process = processStarter.start(builder);
                    builder.environment().clear();
                    return new RunningProcess(process);
                } catch (IOException exception) {
                    throw SnapshotManifest.invalid();
                } finally {
                    builder.environment().clear();
                }
            }

            InputStream stdout() {
                return process.getInputStream();
            }

            OutputStream stdin() {
                return process.getOutputStream();
            }

            void closeStdin() {
                try {
                    process.getOutputStream().close();
                } catch (IOException exception) {
                    close();
                    throw SnapshotManifest.invalid();
                }
            }

            synchronized void awaitSuccess() {
                if (waited) {
                    return;
                }
                try {
                    if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        terminate();
                        throw SnapshotManifest.invalid();
                    }
                    stderrDrain.join(Duration.ofSeconds(5));
                    if (stderrDrain.isAlive() || stderr.overflow() || process.exitValue() != 0) {
                        throw SnapshotManifest.invalid();
                    }
                    waited = true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    terminate();
                    throw SnapshotManifest.invalid();
                }
            }

            @Override
            public synchronized void close() {
                if (process.isAlive()) {
                    terminate();
                }
                closeQuietly(process.getInputStream());
                closeQuietly(process.getOutputStream());
                closeQuietly(process.getErrorStream());
            }

            private void terminate() {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                try {
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.descendants().forEach(ProcessHandle::destroyForcibly);
                        process.destroyForcibly();
                        process.waitFor(1, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            }

            private static void closeQuietly(AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // Process cleanup is best effort after the stable failure was selected.
                }
            }
        }

        private static final class BoundedError implements Runnable {
            private final InputStream input;
            private volatile boolean overflow;

            private BoundedError(InputStream input) {
                this.input = input;
            }

            @Override
            public void run() {
                try (input; ByteArrayOutputStream captured = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8_192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        int remaining = STDERR_LIMIT - captured.size();
                        if (remaining > 0) {
                            captured.write(buffer, 0, Math.min(remaining, read));
                        }
                        overflow |= read > Math.max(remaining, 0);
                    }
                } catch (IOException exception) {
                    overflow = true;
                }
            }

            boolean overflow() {
                return overflow;
            }
        }
    }
}
