package com.ycsopen.sms.core.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationCommand;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationLauncher;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Canonical signed-pair fixture that always enters through the production migration launcher. */
final class Phase03MigrationCommandFixture {

    static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    static final String MIGRATION_SET = "phase03-plan14";
    static final String ENVIRONMENT = "integration";
    static final String DATABASE = "1".repeat(64);
    static final String SCHEMA = "phase01";
    static final String FLYWAY = "2".repeat(64);
    static final String WRITER_SOURCE = "3".repeat(64);
    static final String RECOVERY_REFERENCE = "snapshot-recovery.v1";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] PAIR_DOMAIN =
            "YCS-MIGRATION-PAIR/v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGNATURE_DOMAIN =
            "YCS-MIGRATION-PAIR-SIGNATURE/v1\0".getBytes(StandardCharsets.US_ASCII);
    private final Path directory;
    private final AtomicInteger sequence = new AtomicInteger();

    Phase03MigrationCommandFixture(Path directory) throws Exception {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
    }

    PairFiles pair(long globalSequence, String signerVersion, KeyPair signer) throws Exception {
        return pair(globalSequence, signerVersion, signer, node -> { }, node -> { });
    }

    PairFiles pair(
            long globalSequence,
            String signerVersion,
            KeyPair signer,
            Consumer<ObjectNode> writerMutation,
            Consumer<ObjectNode> snapshotMutation) throws Exception {
        ObjectNode writer = writer(globalSequence, signerVersion);
        ObjectNode snapshot = snapshot(globalSequence, signerVersion);
        writerMutation.accept(writer);
        snapshotMutation.accept(snapshot);
        byte[] writerBytes = canonical(writer);
        byte[] snapshotBytes = canonical(snapshot);
        byte[] writerDigest = sha256(writerBytes);
        byte[] snapshotDigest = sha256(snapshotBytes);
        Subject subject = subject(writer);
        byte[] pairDigest = pairDigest(subject, writerDigest, snapshotDigest);

        String prefix = "migration-pair-" + sequence.incrementAndGet();
        Path writerPath = directory.resolve(prefix + "-writer.json");
        Path writerSignature = directory.resolve(prefix + "-writer.sig");
        Path snapshotPath = directory.resolve(prefix + "-snapshot.json");
        Path snapshotSignature = directory.resolve(prefix + "-snapshot.sig");
        Files.write(writerPath, writerBytes);
        Files.write(snapshotPath, snapshotBytes);
        Files.write(writerSignature, sign(signer, (byte) 1, pairDigest, writerDigest));
        Files.write(snapshotSignature, sign(signer, (byte) 2, pairDigest, snapshotDigest));
        return new PairFiles(
                writerPath, writerSignature, snapshotPath, snapshotSignature,
                HexFormat.of().formatHex(pairDigest), subject);
    }

    static MigrationPreflightProperties properties(
            List<MigrationPreflightProperties.SignerAnchor> anchors) {
        return new MigrationPreflightProperties(
                anchors,
                Set.of(new MigrationPreflightProperties.WriterIdentity(
                        "ycsopen-sms-core", "1.0.0", WRITER_SOURCE)),
                Set.of(RECOVERY_REFERENCE));
    }

    static MigrationPreflightProperties.SignerAnchor anchor(
            KeyPair pair,
            String version,
            MigrationPreflightProperties.AnchorState state,
            Long maximumSequence) {
        byte[] encoded = pair.getPublic().getEncoded();
        return new MigrationPreflightProperties.SignerAnchor(
                version, state, HexFormat.of().formatHex(sha256(encoded)),
                Base64.getEncoder().encodeToString(encoded), maximumSequence);
    }

    static CommandResult invoke(
            ProtectedDataMigrationCommand.DefaultServices services, String... arguments) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exit;
        try (PrintStream stdout = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
             PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8)) {
            exit = ProtectedDataMigrationLauncher.run(arguments, stdout, stderr, services);
        }
        return new CommandResult(
                exit, stdoutBytes.toString(StandardCharsets.UTF_8),
                stderrBytes.toString(StandardCharsets.UTF_8));
    }

    static String[] preflightArguments(PairFiles pair) {
        return preflightArguments(pair, ENVIRONMENT, DATABASE, SCHEMA, FLYWAY);
    }

    static String[] preflightArguments(
            PairFiles pair, String environment, String database, String schema, String flyway) {
        return new String[]{
                "preflight",
                "--writer-manifest", pair.writerManifest().toString(),
                "--writer-signature", pair.writerSignature().toString(),
                "--snapshot-manifest", pair.snapshotManifest().toString(),
                "--snapshot-signature", pair.snapshotSignature().toString(),
                "--environment", environment,
                "--database-instance-fingerprint", database,
                "--schema", schema,
                "--flyway-set-digest", flyway
        };
    }

    static String[] batchArguments(
            String operation, String runId, String target, String pairDigest,
            String ownerDigest, int batchSize) {
        return new String[]{
                operation,
                "--run-id", runId,
                "--target", target,
                "--pair-digest", pairDigest,
                "--lease-owner-digest", ownerDigest,
                "--batch-size", Integer.toString(batchSize)
        };
    }

    static String[] advanceArguments(
            String runId, String target, String pairDigest, String ownerDigest, String nextState) {
        return new String[]{
                "advance",
                "--run-id", runId,
                "--target", target,
                "--pair-digest", pairDigest,
                "--lease-owner-digest", ownerDigest,
                "--next-state", nextState
        };
    }

    private static ObjectNode writer(long globalSequence, String signerVersion) {
        ObjectNode root = JSON.createObjectNode();
        shared(root, "ycs-writer-fence/v1", globalSequence, signerVersion);
        root.put("issued_at", NOW.minusSeconds(1).toString());
        root.put("expires_at", NOW.plusSeconds(3600).toString());
        ObjectNode writer = root.putArray("writers").addObject();
        writer.put("artifact_id", "ycsopen-sms-core");
        writer.put("migration_compatible", true);
        writer.put("source_digest", WRITER_SOURCE);
        writer.put("version", "1.0.0");
        return root;
    }

    private static ObjectNode snapshot(long globalSequence, String signerVersion) {
        ObjectNode root = JSON.createObjectNode();
        shared(root, "ycs-encrypted-snapshot/v1", globalSequence, signerVersion);
        root.put("snapshot_id", "plan14-snapshot-" + globalSequence);
        root.put("recovery_key_reference", RECOVERY_REFERENCE);
        root.put("completed", true);
        root.put("total_plaintext_bytes", 1);
        root.put("total_envelope_bytes", 146);
        root.put("chunk_count", 1);
        ObjectNode chunk = root.putArray("chunks").addObject();
        chunk.put("index", 0);
        chunk.put("final", true);
        chunk.put("plaintext_size", 1);
        chunk.put("envelope_size", 146);
        chunk.put("sha256_digest", "4".repeat(64));
        return root;
    }

    private static void shared(
            ObjectNode root, String manifestSchema, long globalSequence, String signerVersion) {
        root.put("manifest_schema", manifestSchema);
        root.put("migration_set_id", MIGRATION_SET);
        root.put("environment", ENVIRONMENT);
        root.put("database_instance_fingerprint", DATABASE);
        root.put("schema", SCHEMA);
        root.put("flyway_set_digest", FLYWAY);
        root.put("global_sequence", globalSequence);
        root.put("signer_key_version", signerVersion);
    }

    private static Subject subject(ObjectNode writer) {
        return new Subject(
                writer.required("migration_set_id").asText(),
                writer.required("environment").asText(),
                writer.required("database_instance_fingerprint").asText(),
                writer.required("schema").asText(),
                writer.required("flyway_set_digest").asText(),
                writer.required("global_sequence").asLong(),
                writer.required("signer_key_version").asText());
    }

    private static byte[] pairDigest(
            Subject subject, byte[] writerDigest, byte[] snapshotDigest) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(PAIR_DOMAIN);
        for (String value : List.of(
                subject.migrationSetId(), subject.environment(), subject.database(),
                subject.schema(), subject.flyway())) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            output.writeBytes(bytes);
        }
        output.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(subject.globalSequence()).array());
        byte[] signer = subject.signerVersion().getBytes(StandardCharsets.UTF_8);
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(signer.length).array());
        output.writeBytes(signer);
        output.writeBytes(writerDigest);
        output.writeBytes(snapshotDigest);
        return sha256(output.toByteArray());
    }

    private static byte[] sign(
            KeyPair pair, byte role, byte[] pairDigest, byte[] roleDigest) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(SIGNATURE_DOMAIN);
        signature.update(role);
        signature.update(pairDigest);
        signature.update(roleDigest);
        return signature.sign();
    }

    private static byte[] canonical(JsonNode node) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeCanonical(node, output);
        return output.toByteArray();
    }

    private static void writeCanonical(JsonNode node, ByteArrayOutputStream output) throws Exception {
        if (node.isObject()) {
            output.write('{');
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.sort(Comparator.naturalOrder());
            for (int index = 0; index < fields.size(); index++) {
                if (index > 0) {
                    output.write(',');
                }
                String field = fields.get(index);
                output.writeBytes(JSON.writeValueAsBytes(field));
                output.write(':');
                writeCanonical(node.get(field), output);
            }
            output.write('}');
        } else if (node instanceof ArrayNode array) {
            output.write('[');
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    output.write(',');
                }
                writeCanonical(array.get(index), output);
            }
            output.write(']');
        } else if (node.isTextual()) {
            output.writeBytes(JSON.writeValueAsBytes(node.textValue()));
        } else if (node.isBoolean()) {
            output.writeBytes(Boolean.toString(node.booleanValue()).getBytes(StandardCharsets.US_ASCII));
        } else if (node.isIntegralNumber()) {
            output.writeBytes(node.bigIntegerValue().toString().getBytes(StandardCharsets.US_ASCII));
        } else {
            throw new IllegalArgumentException("fixture contains noncanonical JSON value");
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    record PairFiles(
            Path writerManifest,
            Path writerSignature,
            Path snapshotManifest,
            Path snapshotSignature,
            String pairDigest,
            Subject subject) {
    }

    record Subject(
            String migrationSetId,
            String environment,
            String database,
            String schema,
            String flyway,
            long globalSequence,
            String signerVersion) {
    }

    record CommandResult(int exit, String stdout, String stderr) {
    }
}
