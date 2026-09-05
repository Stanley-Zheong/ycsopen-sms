package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotManifest;
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

/** Test-only adapter for the digest-pinned MySQL fixture container. */
final class DockerMySqlSnapshotProcess implements MySqlSnapshotProcess {
    private static final int STDERR_LIMIT = 65_536;
    private static final String CONTAINER_HOST = "127.0.0.1";
    private static final int CONTAINER_PORT = 3306;
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(15);
    private static final Pattern CONTAINER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private final List<String> commandPrefix;

    DockerMySqlSnapshotProcess(Path docker, String containerName) {
        if (containerName == null || !CONTAINER.matcher(containerName).matches()) {
            throw SnapshotManifest.invalid();
        }
        commandPrefix = List.of(
                executable(docker).toString(), "exec", "--interactive",
                "--env", "MYSQL_PWD", containerName);
    }

    @Override
    public DumpSession startDump(Database source) {
        Objects.requireNonNull(source, "source");
        List<String> argv = new ArrayList<>(commandPrefix);
        argv.add("mysqldump");
        argv.add("--no-defaults");
        argv.add("--protocol=TCP");
        argv.add("--host=" + CONTAINER_HOST);
        argv.add("--port=" + CONTAINER_PORT);
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
                "mysql", "--no-defaults", "--protocol=TCP", "--host=" + CONTAINER_HOST,
                "--port=" + CONTAINER_PORT, "--user=" + target.username(),
                "--binary-mode", "--default-character-set=utf8mb4",
                "--database=" + target.schema()));
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
            stderr = new BoundedError(process.getErrorStream());
            stderrDrain = Thread.ofVirtual().name("mysql-fixture-stderr").start(stderr);
        }

        private static RunningProcess start(List<String> argv, char[] password) {
            ProcessBuilder builder = new ProcessBuilder(argv);
            String credential = new String(password);
            Arrays.fill(password, '\0');
            try {
                builder.environment().clear();
                builder.environment().put("MYSQL_PWD", credential);
                builder.environment().put("LC_ALL", "C");
                builder.environment().put("LANG", "C");
                builder.environment().put("TZ", "UTC");
                Process process = builder.start();
                builder.environment().clear();
                return new RunningProcess(process);
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            } finally {
                builder.environment().clear();
            }
        }

        private InputStream stdout() {
            return process.getInputStream();
        }

        private OutputStream stdin() {
            return process.getOutputStream();
        }

        private void closeStdin() {
            try {
                process.getOutputStream().close();
            } catch (IOException exception) {
                close();
                throw SnapshotManifest.invalid();
            }
        }

        private synchronized void awaitSuccess() {
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
                // Test fixture cleanup is best effort after the stable failure was selected.
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

        private boolean overflow() {
            return overflow;
        }
    }
}
