package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.RunState;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.RunStatus;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.BatchResult;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.MigrationRequest;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.RunControlRequest;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.FailureCode;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.VerificationException;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed command surface for the explicit Phase-03 migration. */
public final class ProtectedDataMigrationCommand {

    static final String HELP = """
            phase03-migration <command> [options]

            Commands:
              preflight  Admit one signed writer/snapshot manifest pair
              start      Start one bounded target batch
              resume     Resume one bounded target batch
              pause      Pause an admitted migration run
              abort      Abort forward migration without plaintext rollback
              status     Print sanitized run counters

            preflight options (all required):
              --writer-manifest PATH
              --writer-signature PATH
              --snapshot-manifest PATH
              --snapshot-signature PATH
              --environment ID
              --database-instance-fingerprint HEX
              --schema NAME
              --flyway-set-digest HEX

            start/resume options (all required):
              --run-id ID
              --target REVIEWED_TARGET
              --pair-digest HEX
              --lease-owner-digest HEX
              --batch-size 1..1000

            pause/abort options (all required):
              --run-id ID
              --pair-digest HEX

            status options (all required):
              --run-id ID

            Exit codes: 0 accepted; 20 invocation/path; 21 canonical/schema;
                        22 signature/trust; 23 subject; 24 writer/replay;
                        25 snapshot; 26 key/provider.
            """;

    private static final Set<String> COMMANDS = Set.of(
            "preflight", "start", "resume", "pause", "abort", "status");
    private static final List<String> PREFLIGHT_OPTIONS = List.of(
            "--writer-manifest", "--writer-signature", "--snapshot-manifest",
            "--snapshot-signature", "--environment", "--database-instance-fingerprint",
            "--schema", "--flyway-set-digest");
    private static final List<String> BATCH_OPTIONS = List.of(
            "--run-id", "--target", "--pair-digest", "--lease-owner-digest", "--batch-size");
    private static final List<String> MUTATION_OPTIONS = List.of("--run-id", "--pair-digest");
    private static final List<String> STATUS_OPTIONS = List.of("--run-id");

    private final CommandServices services;

    public ProtectedDataMigrationCommand(CommandServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /**
     * The help path constructs no Spring context and touches no file, database or key provider.
     * Production launchers should inject {@link DefaultServices} for all other operations.
     */
    public static void main(String[] args) {
        int exit = ProtectedDataMigrationLauncher.run(args, System.out, System.err);
        if (exit != Exit.ACCEPTED.code()) {
            System.exit(exit);
        }
    }

    public int execute(String[] args, PrintStream stdout, PrintStream stderr) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        if (args.length == 1 && "--help".equals(args[0])) {
            stdout.print(HELP);
            return Exit.ACCEPTED.code();
        }
        try {
            if (args.length == 0 || !COMMANDS.contains(args[0])) {
                throw commandFailure(Exit.INVOCATION_OR_PATH);
            }
            return switch (args[0]) {
                case "preflight" -> preflight(parse(args, PREFLIGHT_OPTIONS), stdout);
                case "start", "resume" -> batch(args[0], parse(args, BATCH_OPTIONS), stdout);
                case "pause", "abort" -> mutate(args[0], parse(args, MUTATION_OPTIONS), stdout);
                case "status" -> status(parse(args, STATUS_OPTIONS), stdout);
                default -> throw commandFailure(Exit.INVOCATION_OR_PATH);
            };
        } catch (CommandException exception) {
            stderr.print("phase03-migration:error:" + exception.exit().wireName() + "\n");
            return exception.exit().code();
        } catch (VerificationException exception) {
            Exit exit = verifierExit(exception.code());
            stderr.print("phase03-migration:error:" + exit.wireName() + "\n");
            return exit.code();
        } catch (IllegalArgumentException exception) {
            stderr.print("phase03-migration:error:" + Exit.INVOCATION_OR_PATH.wireName() + "\n");
            return Exit.INVOCATION_OR_PATH.code();
        } catch (RuntimeException exception) {
            stderr.print("phase03-migration:error:" + Exit.KEY_OR_PROVIDER.wireName() + "\n");
            return Exit.KEY_OR_PROVIDER.code();
        }
    }

    private int preflight(Map<String, String> options, PrintStream stdout) {
        PreflightInvocation invocation = new PreflightInvocation(
                Path.of(options.get("--writer-manifest")),
                Path.of(options.get("--writer-signature")),
                Path.of(options.get("--snapshot-manifest")),
                Path.of(options.get("--snapshot-signature")),
                options.get("--environment"),
                options.get("--database-instance-fingerprint"),
                options.get("--schema"),
                options.get("--flyway-set-digest"));
        PairedAdmission admission = services.preflight(invocation);
        if (admission == null) {
            throw commandFailure(Exit.KEY_OR_PROVIDER);
        }
        stdout.print("{\"status\":\"accepted\",\"pair_digest\":\""
                + admission.pairDigest() + "\",\"global_sequence\":"
                + Long.toUnsignedString(admission.globalSequence()) + "}\n");
        return Exit.ACCEPTED.code();
    }

    private int batch(String command, Map<String, String> options, PrintStream stdout) {
        String pairDigest = options.get("--pair-digest");
        requireAcceptedPair(pairDigest);
        int batchSize;
        try {
            batchSize = Integer.parseInt(options.get("--batch-size"));
        } catch (NumberFormatException exception) {
            throw commandFailure(Exit.INVOCATION_OR_PATH);
        }
        MigrationRequest request = new MigrationRequest(
                options.get("--run-id"), options.get("--target"), pairDigest,
                options.get("--lease-owner-digest"), batchSize,
                ProtectedDataMigrationRunner.DEFAULT_LEASE_DURATION);
        BatchResult result = "start".equals(command)
                ? services.start(request) : services.resume(request);
        if (result == null) {
            throw commandFailure(Exit.KEY_OR_PROVIDER);
        }
        stdout.print("{\"status\":\"accepted\",\"run_id\":\"" + result.runId()
                + "\",\"target\":\"" + result.targetId() + "\",\"scanned\":"
                + result.scanned() + ",\"migrated\":" + result.migrated()
                + ",\"verified\":" + result.verified() + ",\"skipped\":"
                + result.skipped() + ",\"end_of_target\":" + result.endOfTarget() + "}\n");
        return Exit.ACCEPTED.code();
    }

    private int mutate(String command, Map<String, String> options, PrintStream stdout) {
        String pairDigest = options.get("--pair-digest");
        requireAcceptedPair(pairDigest);
        RunControlRequest request = new RunControlRequest(options.get("--run-id"), pairDigest);
        if ("pause".equals(command)) {
            services.pause(request);
        } else {
            services.abort(request);
        }
        stdout.print("{\"status\":\"accepted\",\"run_id\":\""
                + request.runId() + "\",\"state\":\""
                + ("pause".equals(command) ? "PAUSED" : "ABORTED") + "\"}\n");
        return Exit.ACCEPTED.code();
    }

    private int status(Map<String, String> options, PrintStream stdout) {
        RunStatus status = services.status(options.get("--run-id"));
        if (status == null) {
            throw commandFailure(Exit.KEY_OR_PROVIDER);
        }
        stdout.print("{\"status\":\"accepted\",\"run_id\":\"" + status.runId()
                + "\",\"state\":\"" + status.state() + "\",\"pair_digest\":\""
                + status.acceptedPairDigest() + "\",\"scanned\":" + status.scanned()
                + ",\"migrated\":" + status.migrated() + ",\"verified\":"
                + status.verified() + ",\"quarantined\":" + status.quarantined() + "}\n");
        return Exit.ACCEPTED.code();
    }

    private void requireAcceptedPair(String pairDigest) {
        if (!services.acceptedPair(pairDigest)) {
            throw commandFailure(Exit.WRITER_OR_REPLAY);
        }
    }

    private static Map<String, String> parse(String[] args, List<String> expected) {
        if (args.length != 1 + expected.size() * 2) {
            throw commandFailure(Exit.INVOCATION_OR_PATH);
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        Set<String> allowed = Set.copyOf(expected);
        for (int index = 1; index < args.length; index += 2) {
            String option = args[index];
            String value = args[index + 1];
            if (!allowed.contains(option) || value == null || value.isBlank()
                    || value.startsWith("--") || parsed.put(option, value) != null) {
                throw commandFailure(Exit.INVOCATION_OR_PATH);
            }
        }
        if (!new ArrayList<>(parsed.keySet()).containsAll(expected)
                || parsed.size() != expected.size()) {
            throw commandFailure(Exit.INVOCATION_OR_PATH);
        }
        return Map.copyOf(parsed);
    }

    private static Exit verifierExit(FailureCode code) {
        return switch (code) {
            case PATH_INVALID -> Exit.INVOCATION_OR_PATH;
            case CANONICAL_SCHEMA_INVALID -> Exit.CANONICAL_OR_SCHEMA;
            case SIGNER_NOT_TRUSTED, SIGNATURE_INVALID -> Exit.SIGNATURE_OR_TRUST;
            case SUBJECT_MISMATCH -> Exit.SUBJECT_MISMATCH;
            case WRITER_SET_INVALID, REPLAY_OR_PAIR_CONFLICT, PAIR_ADMISSION_FAILED ->
                    Exit.WRITER_OR_REPLAY;
            case SNAPSHOT_INVENTORY_INVALID -> Exit.SNAPSHOT_INVALID;
        };
    }

    private static CommandException commandFailure(Exit exit) {
        return new CommandException(exit);
    }

    public interface CommandServices {
        PairedAdmission preflight(PreflightInvocation invocation);

        boolean acceptedPair(String pairDigest);

        BatchResult start(MigrationRequest request);

        BatchResult resume(MigrationRequest request);

        void pause(RunControlRequest request);

        void abort(RunControlRequest request);

        RunStatus status(String runId);
    }

    @FunctionalInterface
    public interface PreflightOperation {
        PairedAdmission admit(PreflightInvocation invocation);
    }

    /** Adapter used by a production launcher after it has explicitly constructed dependencies. */
    public static final class DefaultServices implements CommandServices {
        private final PreflightOperation preflight;
        private final MigrationStateRepository repository;
        private final ProtectedDataMigrationRunner runner;

        public DefaultServices(
                PreflightOperation preflight,
                MigrationStateRepository repository,
                ProtectedDataMigrationRunner runner) {
            this.preflight = Objects.requireNonNull(preflight, "preflight");
            this.repository = Objects.requireNonNull(repository, "repository");
            this.runner = Objects.requireNonNull(runner, "runner");
        }

        @Override
        public PairedAdmission preflight(PreflightInvocation invocation) {
            return preflight.admit(invocation);
        }

        @Override
        public boolean acceptedPair(String pairDigest) {
            try {
                return repository.transaction(transaction -> {
                    transaction.requireAcceptedPair(pairDigest);
                    return Boolean.TRUE;
                });
            } catch (RuntimeException exception) {
                return false;
            }
        }

        @Override
        public BatchResult start(MigrationRequest request) {
            return runner.migrateBatch(request);
        }

        @Override
        public BatchResult resume(MigrationRequest request) {
            return runner.migrateBatch(request);
        }

        @Override
        public void pause(RunControlRequest request) {
            runner.changeRunState(request, RunState.RUNNING, RunState.PAUSED);
        }

        @Override
        public void abort(RunControlRequest request) {
            RunStatus status = runner.status(request.runId());
            RunState expected = status.state() == RunState.PAUSED ? RunState.PAUSED : RunState.RUNNING;
            runner.changeRunState(request, expected, RunState.ABORTED);
        }

        @Override
        public RunStatus status(String runId) {
            return runner.status(runId);
        }
    }

    public record PreflightInvocation(
            Path writerManifest,
            Path writerSignature,
            Path snapshotManifest,
            Path snapshotSignature,
            String environment,
            String databaseInstanceFingerprint,
            String schema,
            String flywaySetDigest) {

        public PreflightInvocation {
            Objects.requireNonNull(writerManifest, "writerManifest");
            Objects.requireNonNull(writerSignature, "writerSignature");
            Objects.requireNonNull(snapshotManifest, "snapshotManifest");
            Objects.requireNonNull(snapshotSignature, "snapshotSignature");
            if (environment == null || environment.isBlank()
                    || databaseInstanceFingerprint == null
                    || !MigrationStateRepository.SHA256.matcher(databaseInstanceFingerprint).matches()
                    || schema == null || schema.isBlank()
                    || flywaySetDigest == null
                    || !MigrationStateRepository.SHA256.matcher(flywaySetDigest).matches()) {
                throw new IllegalArgumentException("preflight subject is invalid");
            }
        }
    }

    public enum Exit {
        ACCEPTED(0, "accepted"),
        INVOCATION_OR_PATH(20, "invocation_or_path"),
        CANONICAL_OR_SCHEMA(21, "canonical_or_schema"),
        SIGNATURE_OR_TRUST(22, "signature_or_trust"),
        SUBJECT_MISMATCH(23, "subject_mismatch"),
        WRITER_OR_REPLAY(24, "writer_or_replay"),
        SNAPSHOT_INVALID(25, "snapshot_invalid"),
        KEY_OR_PROVIDER(26, "key_or_provider");

        private final int code;
        private final String wireName;

        Exit(int code, String wireName) {
            this.code = code;
            this.wireName = wireName;
        }

        public int code() {
            return code;
        }

        public String wireName() {
            return wireName;
        }
    }

    public static final class CommandException extends IllegalStateException {
        private final Exit exit;

        public CommandException(Exit exit) {
            super("phase03 migration command rejected");
            this.exit = Objects.requireNonNull(exit, "exit");
        }

        public Exit exit() {
            return exit;
        }
    }

}
