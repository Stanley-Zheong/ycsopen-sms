package com.ycsopen.sms.core.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class Phase08OwnedProcessTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void timeoutForcesAndReapsAGracefulResistantProcessTree() throws Exception {
        Path diagnostic = temporaryDirectory.resolve("timeout.log");
        Phase08RealServicePlaywrightTest.OwnedProcess.Result result =
                Phase08RealServicePlaywrightTest.OwnedProcess.run(
                        shell("""
                                trap '' TERM
                                (trap '' TERM; while :; do sleep 1; done) &
                                child=$!
                                printf 'parent=%s child=%s\n' "$$" "$child"
                                wait "$child"
                                """),
                        Duration.ofMillis(500), diagnostic, Map.of());

        long[] pids = pids(result.output());
        assertThat(result.timedOut()).isTrue();
        assertThat(result.forcedProcessCount()).isGreaterThanOrEqualTo(1);
        assertDead(pids);
        assertThat(diagnostic).hasContent(result.output());
    }

    @Test
    void interruptedWaitRestoresTheFlagAndReapsTheProcessTree() throws Exception {
        Path pidFile = temporaryDirectory.resolve("interrupted.pids");
        Path diagnostic = temporaryDirectory.resolve("interrupted.log");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread owner = Thread.ofPlatform().start(() -> {
            try {
                Phase08RealServicePlaywrightTest.OwnedProcess.run(
                        shell("""
                                trap '' TERM
                                (trap '' TERM; while :; do sleep 1; done) &
                                child=$!
                                printf '%s:%s' "$$" "$child" > "$1"
                                wait "$child"
                                """, pidFile.toString()),
                        Duration.ofSeconds(20), diagnostic, Map.of());
            } catch (Throwable thrown) {
                failure.set(thrown);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });

        try {
            awaitContent(pidFile);
            long[] pids = Files.readString(pidFile).lines()
                    .flatMap(line -> java.util.Arrays.stream(line.split(":")))
                    .mapToLong(Long::parseLong).toArray();
            owner.interrupt();
            owner.join(Duration.ofSeconds(5));

            assertThat(owner.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(InterruptedException.class);
            assertThat(interruptRestored).isTrue();
            assertDead(pids);
            assertThat(diagnostic).exists();
        } finally {
            if (owner.isAlive()) {
                owner.interrupt();
                owner.join(Duration.ofSeconds(5));
            }
        }
    }

    @Test
    void normalFailureRetainsOnlyABoundedSanitizedTailAndReapsDescendants() throws Exception {
        Path diagnostic = temporaryDirectory.resolve("failure.log");
        String marker = "synthetic-secret-marker";
        Phase08RealServicePlaywrightTest.OwnedProcess.Result result =
                Phase08RealServicePlaywrightTest.OwnedProcess.run(
                        shell("""
                                (trap '' TERM; while :; do sleep 1; done) &
                                child=$!
                                i=0
                                while [ "$i" -lt 900 ]; do
                                  printf 'bounded-output-line-%04d-abcdefghijklmnopqrstuvwxyz\n' "$i"
                                  i=$((i + 1))
                                done
                                printf 'parent=%s child=%s secret=%s\n' "$$" "$child" "$1"
                                sleep 1
                                exit 7
                                """, marker),
                        Duration.ofSeconds(5), diagnostic,
                        Map.of("SYNTHETIC_SECRET", marker));

        long[] pids = pids(result.output());
        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isEqualTo(7);
        assertThat(result.forcedProcessCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.output()).doesNotContain(marker).contains("[REDACTED]");
        assertThat(result.output().length())
                .isLessThanOrEqualTo(Phase08RealServicePlaywrightTest.OwnedProcess.TAIL_LIMIT);
        assertThat(Files.readString(diagnostic))
                .isEqualTo(result.output())
                .doesNotContain(marker);
        assertThat(Files.size(diagnostic))
                .isLessThanOrEqualTo(Phase08RealServicePlaywrightTest.OwnedProcess.TAIL_LIMIT);
        assertDead(pids);
    }

    private static ProcessBuilder shell(String script, String... arguments) {
        String[] command = new String[arguments.length + 4];
        command[0] = "/bin/sh";
        command[1] = "-c";
        command[2] = script;
        command[3] = "phase08-owned-process";
        System.arraycopy(arguments, 0, command, 4, arguments.length);
        return new ProcessBuilder(command).redirectErrorStream(true);
    }

    private static long[] pids(String output) {
        String line = output.lines().filter(candidate -> candidate.contains("parent="))
                .findFirst().orElseThrow();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("parent=(\\d+)\\s+child=(\\d+)").matcher(line);
        assertThat(matcher.find()).isTrue();
        return new long[]{Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))};
    }

    private static void assertDead(long[] pids) {
        for (long pid : pids) {
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                    .as("owned process %s must be reaped", pid).isFalse();
        }
    }

    private static void awaitContent(Path path) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while ((!Files.isRegularFile(path) || Files.size(path) == 0)
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(path).isNotEmptyFile();
    }
}
