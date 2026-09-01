package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.RunState;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.RunStatus;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationCommand.CommandException;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationCommand.CommandServices;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationCommand.Exit;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationCommand.PreflightInvocation;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.BatchResult;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.MigrationRequest;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.RunControlRequest;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectedDataMigrationCommandTest {

    private static final String DIGEST = "a".repeat(64);
    private static final String OTHER_DIGEST = "b".repeat(64);
    private static final String RUN_ID = "00000000-0000-4000-8000-000000000001";

    @Test
    void helpIsByteGoldenAndLoadsNoCollaborator() throws IOException {
        RecordingServices services = new RecordingServices();

        Invocation result = invoke(services, "--help");

        assertThat(result.exit()).isZero();
        assertThat(result.stdout()).isEqualTo(Files.readString(
                Path.of("src/test/resources/security/migration/phase03-migration-help.txt")));
        assertThat(result.stderr()).isEmpty();
        assertThat(services.totalCalls()).isZero();
    }

    @Test
    void preflightConsumesExactlyEightOptionsAsOneRequest() {
        RecordingServices services = new RecordingServices();

        Invocation result = invoke(services, validPreflight());

        assertThat(result.exit()).isZero();
        assertThat(result.stdout()).containsOnlyOnce("\"pair_digest\":\"" + DIGEST + "\"");
        assertThat(result.stderr()).isEmpty();
        assertThat(services.preflightCalls).isOne();
        assertThat(services.lastPreflight.writerManifest()).isEqualTo(Path.of("/tmp/writer.json"));
        assertThat(services.lastPreflight.snapshotManifest()).isEqualTo(Path.of("/tmp/snapshot.json"));
        assertThat(services.pairWrites).isOne();
        assertThat(services.mutationCalls).isZero();
    }

    @ParameterizedTest(name = "rejects malformed argv: {0}")
    @MethodSource("malformedInvocations")
    void unknownDuplicateAndMissingOptionsFailBeforeEveryCollaborator(
            String description, String[] arguments) {
        RecordingServices services = new RecordingServices();

        Invocation result = invoke(services, arguments);

        assertThat(result.exit()).isEqualTo(20);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isEqualTo(
                "phase03-migration:error:invocation_or_path\n");
        assertThat(services.totalCalls()).isZero();
    }

    @ParameterizedTest(name = "exit {0}")
    @MethodSource("failureExits")
    void everyFailureCategoryIsStableAndLeavesPairAndMigrationStateUnchanged(Exit exit) {
        RecordingServices services = new RecordingServices();
        services.failure = exit;

        Invocation result = invoke(services, validPreflight());

        assertThat(result.exit()).isEqualTo(exit.code());
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isEqualTo(
                "phase03-migration:error:" + exit.wireName() + "\n");
        assertThat(services.pairWrites).isZero();
        assertThat(services.mutationCalls).isZero();
    }

    @ParameterizedTest(name = "paired fault {0}")
    @MethodSource("pairedFaults")
    void spliceReplaySequenceChangeAndHalfWriteHaveOneWriterPairExit(
            String ignoredFaultName) {
        RecordingServices services = new RecordingServices();
        services.failure = Exit.WRITER_OR_REPLAY;

        Invocation result = invoke(services, validPreflight());

        assertThat(result.exit()).isEqualTo(24);
        assertThat(services.pairWrites).isZero();
        assertThat(services.mutationCalls).isZero();
    }

    @Test
    void noMutatingCommandRunsWithoutTheCurrentlyAcceptedPairDigest() {
        RecordingServices services = new RecordingServices();
        services.pairAccepted = false;

        Invocation start = invoke(services,
                "start", "--run-id", RUN_ID, "--target", "bulk_sending_items.mobile_encrypted",
                "--pair-digest", DIGEST, "--lease-owner-digest", OTHER_DIGEST,
                "--batch-size", "10");
        Invocation pause = invoke(services,
                "pause", "--run-id", RUN_ID, "--pair-digest", DIGEST);
        Invocation abort = invoke(services,
                "abort", "--run-id", RUN_ID, "--pair-digest", DIGEST);

        assertThat(List.of(start.exit(), pause.exit(), abort.exit())).containsOnly(24);
        assertThat(services.acceptedPairCalls).isEqualTo(3);
        assertThat(services.mutationCalls).isZero();
    }

    @Test
    void exposesOnlyTheSixFixedSubcommandsAndSanitizedResults() {
        RecordingServices services = new RecordingServices();
        Invocation start = invoke(services,
                "start", "--run-id", RUN_ID, "--target", "bulk_sending_items.mobile_encrypted",
                "--pair-digest", DIGEST, "--lease-owner-digest", OTHER_DIGEST,
                "--batch-size", "10");
        Invocation resume = invoke(services,
                "resume", "--run-id", RUN_ID, "--target", "bulk_sending_items.mobile_encrypted",
                "--pair-digest", DIGEST, "--lease-owner-digest", OTHER_DIGEST,
                "--batch-size", "10");
        Invocation pause = invoke(services,
                "pause", "--run-id", RUN_ID, "--pair-digest", DIGEST);
        Invocation abort = invoke(services,
                "abort", "--run-id", RUN_ID, "--pair-digest", DIGEST);
        Invocation status = invoke(services, "status", "--run-id", RUN_ID);

        assertThat(List.of(start.exit(), resume.exit(), pause.exit(), abort.exit(), status.exit()))
                .containsOnly(0);
        assertThat(services.mutationCalls).isEqualTo(4);
        assertThat(status.stdout()).contains("\"scanned\":1").doesNotContain("plaintext");
        assertThat(invoke(services, "rollback").exit()).isEqualTo(20);
    }

    private static Stream<Arguments> malformedInvocations() {
        String[] valid = validPreflight();
        String[] missing = java.util.Arrays.copyOf(valid, valid.length - 2);
        String[] duplicate = valid.clone();
        duplicate[15] = "--environment";
        String[] unknown = valid.clone();
        unknown[1] = "--writer-only";
        return Stream.of(
                Arguments.of("empty", new String[0]),
                Arguments.of("unknown command", new String[]{"migrate"}),
                Arguments.of("missing option", missing),
                Arguments.of("duplicate option", duplicate),
                Arguments.of("unknown option", unknown),
                Arguments.of("option without value", new String[]{"status", "--run-id"}));
    }

    private static Stream<Exit> failureExits() {
        return Stream.of(
                Exit.INVOCATION_OR_PATH,
                Exit.CANONICAL_OR_SCHEMA,
                Exit.SIGNATURE_OR_TRUST,
                Exit.SUBJECT_MISMATCH,
                Exit.WRITER_OR_REPLAY,
                Exit.SNAPSHOT_INVALID,
                Exit.KEY_OR_PROVIDER);
    }

    private static Stream<String> pairedFaults() {
        return Stream.of("cross-pair-splice", "separate-replay",
                "same-sequence-digest-change", "simulated-half-write");
    }

    private static String[] validPreflight() {
        return new String[]{
                "preflight",
                "--writer-manifest", "/tmp/writer.json",
                "--writer-signature", "/tmp/writer.sig",
                "--snapshot-manifest", "/tmp/snapshot.json",
                "--snapshot-signature", "/tmp/snapshot.sig",
                "--environment", "test",
                "--database-instance-fingerprint", DIGEST,
                "--schema", "phase03",
                "--flyway-set-digest", OTHER_DIGEST};
    }

    private static Invocation invoke(RecordingServices services, String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exit = new ProtectedDataMigrationCommand(services).execute(arguments, out, err);
        }
        return new Invocation(exit, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int exit, String stdout, String stderr) {
    }

    private static final class RecordingServices implements CommandServices {
        private Exit failure;
        private boolean pairAccepted = true;
        private int preflightCalls;
        private int acceptedPairCalls;
        private int mutationCalls;
        private int pairWrites;
        private PreflightInvocation lastPreflight;

        @Override
        public PairedAdmission preflight(PreflightInvocation invocation) {
            preflightCalls++;
            lastPreflight = invocation;
            failIfRequested();
            pairWrites++;
            return new PairedAdmission(7, "signer-v1", OTHER_DIGEST, "c".repeat(64),
                    DIGEST, Set.of("core-v1"), "snapshot-v1", "snapshot-key-v1");
        }

        @Override
        public boolean acceptedPair(String pairDigest) {
            acceptedPairCalls++;
            return pairAccepted && DIGEST.equals(pairDigest);
        }

        @Override
        public BatchResult start(MigrationRequest request) {
            mutationCalls++;
            return batch(request);
        }

        @Override
        public BatchResult resume(MigrationRequest request) {
            mutationCalls++;
            return batch(request);
        }

        @Override
        public void pause(RunControlRequest request) {
            mutationCalls++;
        }

        @Override
        public void abort(RunControlRequest request) {
            mutationCalls++;
        }

        @Override
        public RunStatus status(String runId) {
            return new RunStatus(runId, RunState.RUNNING, DIGEST, 1, 1, 1, 0);
        }

        private BatchResult batch(MigrationRequest request) {
            return new BatchResult(request.runId(), request.targetId(), 1, 1, 1, 0, 1L, true);
        }

        private void failIfRequested() {
            if (failure != null) {
                throw new CommandException(failure);
            }
        }

        private int totalCalls() {
            return preflightCalls + acceptedPairCalls + mutationCalls;
        }
    }
}
