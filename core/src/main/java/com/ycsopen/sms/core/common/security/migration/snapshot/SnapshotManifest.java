package com.ycsopen.sms.core.common.security.migration.snapshot;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.migration.EncryptedSnapshotVerifier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical, bounded inventory for one complete encrypted MySQL snapshot. */
public record SnapshotManifest(Subject subject,
                               String snapshotId,
                               String recoveryKeyReference,
                               long totalPlaintextBytes,
                               long totalEnvelopeBytes,
                               List<Chunk> chunks) {

    public static final String SCHEMA = "ycs-encrypted-snapshot/v1";
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern ENVIRONMENT = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern DATABASE_SCHEMA = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern KEY_REFERENCE = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "manifest_schema", "migration_set_id", "environment",
            "database_instance_fingerprint", "schema", "flyway_set_digest",
            "global_sequence", "signer_key_version", "snapshot_id",
            "recovery_key_reference", "completed", "total_plaintext_bytes",
            "total_envelope_bytes", "chunk_count", "chunks");
    private static final Set<String> CHUNK_FIELDS = Set.of(
            "index", "final", "plaintext_size", "envelope_size", "sha256_digest");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    public SnapshotManifest {
        Objects.requireNonNull(subject, "subject");
        require(ID, snapshotId, "snapshotId");
        require(KEY_REFERENCE, recoveryKeyReference, "recoveryKeyReference");
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.isEmpty() || chunks.size() > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT
                || totalPlaintextBytes < 1
                || totalPlaintextBytes > EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES
                || totalEnvelopeBytes < 1
                || totalEnvelopeBytes > EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES) {
            throw invalid();
        }
        long plaintext = 0;
        long envelope = 0;
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            if (chunk.index() != index || chunk.terminal() != (index == chunks.size() - 1)) {
                throw invalid();
            }
            plaintext = checkedAdd(plaintext, chunk.plaintextSize());
            envelope = checkedAdd(envelope, chunk.envelopeSize());
        }
        if (plaintext != totalPlaintextBytes || envelope != totalEnvelopeBytes) {
            throw invalid();
        }
    }

    /** Produces exactly the canonical JSON accepted by the paired manifest verifier. */
    public byte[] canonicalBytes() {
        ObjectNode root = JSON.createObjectNode();
        // Fields are inserted in Unicode code-point order. The writer below independently sorts
        // every object, preserving the verifier's canonical representation contract.
        root.put("chunk_count", chunks.size());
        ArrayNode chunkNodes = root.putArray("chunks");
        for (Chunk chunk : chunks) {
            ObjectNode node = chunkNodes.addObject();
            node.put("envelope_size", chunk.envelopeSize());
            node.put("final", chunk.terminal());
            node.put("index", chunk.index());
            node.put("plaintext_size", chunk.plaintextSize());
            node.put("sha256_digest", chunk.sha256Digest());
        }
        root.put("completed", true);
        root.put("database_instance_fingerprint", subject.databaseInstanceFingerprint());
        root.put("environment", subject.environment());
        root.put("flyway_set_digest", subject.flywaySetDigest());
        root.put("global_sequence", subject.globalSequence());
        root.put("manifest_schema", SCHEMA);
        root.put("migration_set_id", subject.migrationSetId());
        root.put("recovery_key_reference", recoveryKeyReference);
        root.put("schema", subject.schema());
        root.put("signer_key_version", subject.signerKeyVersion());
        root.put("snapshot_id", snapshotId);
        root.put("total_envelope_bytes", totalEnvelopeBytes);
        root.put("total_plaintext_bytes", totalPlaintextBytes);
        byte[] canonical = canonicalBytes(root);
        if (canonical.length > EncryptedSnapshotVerifier.MAXIMUM_MANIFEST_BYTES) {
            throw invalid();
        }
        return canonical;
    }

    /** Parses an admitted manifest without accepting noncanonical or trailing input. */
    public static SnapshotManifest parse(byte[] input) {
        if (input == null || input.length < 1
                || input.length > EncryptedSnapshotVerifier.MAXIMUM_MANIFEST_BYTES) {
            throw invalid();
        }
        try {
            JsonNode root = JSON.readTree(input);
            if (root == null || !root.isObject() || !fields(root).equals(TOP_LEVEL_FIELDS)
                    || !MessageDigest.isEqual(input, canonicalBytes(root))
                    || !SCHEMA.equals(text(root, "manifest_schema"))
                    || !bool(root, "completed")) {
                throw invalid();
            }
            long chunkCount = number(root, "chunk_count",
                    EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT);
            JsonNode array = root.get("chunks");
            if (array == null || !array.isArray() || array.size() != chunkCount) {
                throw invalid();
            }
            List<Chunk> chunks = new ArrayList<>(array.size());
            for (JsonNode node : array) {
                if (!fields(node).equals(CHUNK_FIELDS)) {
                    throw invalid();
                }
                chunks.add(new Chunk(
                        Math.toIntExact(number(node, "index",
                                EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT - 1L)),
                        bool(node, "final"),
                        number(node, "plaintext_size",
                                EncryptedSnapshotVerifier.MAXIMUM_CHUNK_PLAINTEXT_BYTES),
                        number(node, "envelope_size",
                                EncryptedSnapshotVerifier.MAXIMUM_CHUNK_ENVELOPE_BYTES),
                        text(node, "sha256_digest")));
            }
            Subject subject = new Subject(
                    text(root, "migration_set_id"), text(root, "environment"),
                    text(root, "database_instance_fingerprint"), text(root, "schema"),
                    text(root, "flyway_set_digest"),
                    number(root, "global_sequence", Long.MAX_VALUE),
                    text(root, "signer_key_version"));
            return new SnapshotManifest(
                    subject, text(root, "snapshot_id"), text(root, "recovery_key_reference"),
                    number(root, "total_plaintext_bytes",
                            EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES),
                    number(root, "total_envelope_bytes",
                            EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES),
                    chunks);
        } catch (SnapshotException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            throw invalid();
        }
    }

    public String digest() {
        return sha256(canonicalBytes());
    }

    public record Subject(String migrationSetId,
                          String environment,
                          String databaseInstanceFingerprint,
                          String schema,
                          String flywaySetDigest,
                          long globalSequence,
                          String signerKeyVersion) {
        public Subject {
            require(ID, migrationSetId, "migrationSetId");
            require(ENVIRONMENT, environment, "environment");
            require(SHA256, databaseInstanceFingerprint, "databaseInstanceFingerprint");
            require(DATABASE_SCHEMA, schema, "schema");
            require(SHA256, flywaySetDigest, "flywaySetDigest");
            require(VERSION, signerKeyVersion, "signerKeyVersion");
            if (globalSequence < 0) {
                throw invalid();
            }
        }
    }

    public record Chunk(int index,
                        boolean terminal,
                        long plaintextSize,
                        long envelopeSize,
                        String sha256Digest) {
        public Chunk {
            if (index < 0 || index >= EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT
                    || plaintextSize < 1
                    || plaintextSize > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_PLAINTEXT_BYTES
                    || envelopeSize <= plaintextSize
                    || envelopeSize > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_ENVELOPE_BYTES) {
                throw invalid();
            }
            require(SHA256, sha256Digest, "sha256Digest");
        }
    }

    private static byte[] canonicalBytes(JsonNode node) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeCanonical(node, output);
        return output.toByteArray();
    }

    private static void writeCanonical(JsonNode node, ByteArrayOutputStream output) {
        try {
            if (node.isObject()) {
                output.write('{');
                List<String> names = new ArrayList<>();
                node.fieldNames().forEachRemaining(names::add);
                names.sort(Comparator.naturalOrder());
                for (int index = 0; index < names.size(); index++) {
                    if (index > 0) {
                        output.write(',');
                    }
                    output.writeBytes(JSON.writeValueAsBytes(names.get(index)));
                    output.write(':');
                    writeCanonical(node.get(names.get(index)), output);
                }
                output.write('}');
            } else if (node.isArray()) {
                output.write('[');
                for (int index = 0; index < node.size(); index++) {
                    if (index > 0) {
                        output.write(',');
                    }
                    writeCanonical(node.get(index), output);
                }
                output.write(']');
            } else if (node.isTextual()) {
                output.writeBytes(JSON.writeValueAsBytes(node.textValue()));
            } else if (node.isBoolean()) {
                output.writeBytes(Boolean.toString(node.booleanValue()).getBytes(StandardCharsets.US_ASCII));
            } else if (node.isIntegralNumber()) {
                output.writeBytes(node.bigIntegerValue().toString().getBytes(StandardCharsets.US_ASCII));
            } else {
                throw invalid();
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private static Set<String> fields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw invalid();
        }
        return value.textValue();
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw invalid();
        }
        return value.booleanValue();
    }

    private static long number(JsonNode node, String field, long maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalid();
        }
        long number = value.longValue();
        if (number < 0 || number > maximum) {
            throw invalid();
        }
        return number;
    }

    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 21 must provide SHA-256", exception);
        }
    }

    private static void require(Pattern pattern, String value, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not canonical");
        }
    }

    static SnapshotException invalid() {
        return new SnapshotException("encrypted snapshot contract rejected");
    }

    /** Stable fail-closed exception; no plaintext or filesystem identity is exposed. */
    public static final class SnapshotException extends IllegalStateException {
        SnapshotException(String message) {
            super(message);
        }
    }
}
