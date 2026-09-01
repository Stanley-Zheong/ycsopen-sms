package com.ycsopen.sms.core.common.security.migration.snapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
        private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(15);
        private static final Pattern CONTAINER =
                Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
        private final List<String> commandPrefix;
        private final String mysqldump;
        private final String mysql;

        public FixedArgumentClient(Path mysqldump, Path mysql) {
            this.commandPrefix = List.of();
            this.mysqldump = executable(mysqldump).toString();
            this.mysql = executable(mysql).toString();
        }

        /** Uses the mysqldump/mysql binaries inside the already identity-pinned fixture image. */
        public FixedArgumentClient(Path docker, String containerName) {
            if (containerName == null || !CONTAINER.matcher(containerName).matches()) {
                throw SnapshotManifest.invalid();
            }
            this.commandPrefix = List.of(
                    executable(docker).toString(), "exec", "--interactive",
                    "--env", "MYSQL_PWD", containerName);
            this.mysqldump = "mysqldump";
            this.mysql = "mysql";
        }

        @Override
        public DumpSession startDump(Database source) {
            Objects.requireNonNull(source, "source");
            List<String> argv = new ArrayList<>();
            argv.addAll(commandPrefix);
            argv.add(mysqldump);
            argv.add("--protocol=TCP");
            argv.add("--host=" + source.host());
            argv.add("--port=" + source.port());
            argv.add("--user=" + source.username());
            argv.add("--single-transaction");
            argv.add("--skip-lock-tables");
            argv.add("--quick");
            argv.add("--hex-blob");
            argv.add("--set-gtid-purged=OFF");
            argv.add("--no-tablespaces");
            argv.add("--skip-comments");
            argv.add("--skip-add-locks");
            argv.add("--column-statistics=0");
            argv.add(source.schema());
            RunningProcess process = RunningProcess.start(argv, source.password());
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
            List<String> argv = new ArrayList<>(commandPrefix);
            argv.addAll(List.of(
                    mysql, "--protocol=TCP", "--host=" + target.host(),
                    "--port=" + target.port(), "--user=" + target.username(),
                    "--binary-mode", "--database=" + target.schema()));
            RunningProcess process = RunningProcess.start(argv, target.password());
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

        private static Path executable(Path candidate) {
            Objects.requireNonNull(candidate, "candidate");
            try {
                Path path = candidate.toAbsolutePath().normalize().toRealPath();
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isExecutable(path)) {
                    throw SnapshotManifest.invalid();
                }
                return path;
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            }
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

            static RunningProcess start(List<String> argv, char[] password) {
                ProcessBuilder builder = new ProcessBuilder(argv);
                String credential = new String(password);
                Arrays.fill(password, '\0');
                try {
                    builder.environment().put("MYSQL_PWD", credential);
                    Process process = builder.start();
                    builder.environment().remove("MYSQL_PWD");
                    return new RunningProcess(process);
                } catch (IOException exception) {
                    throw SnapshotManifest.invalid();
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
