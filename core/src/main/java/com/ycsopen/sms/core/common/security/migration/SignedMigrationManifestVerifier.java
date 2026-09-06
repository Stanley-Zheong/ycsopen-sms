package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.AnchorState;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.SignerAnchor;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.WriterIdentity;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.DeploymentSubject;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmissionRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Production boundary for inseparable writer-fence and encrypted-snapshot admission.
 *
 * <p>All file, schema, subject, inventory, trust and signature checks finish before the singleton
 * pair store is invoked. The store sees one immutable tuple and can therefore never admit one role
 * or expose a half pair.</p>
 */
public final class SignedMigrationManifestVerifier implements WriterFencePort.PairedBoundary {

    static final int MAXIMUM_WRITER_MANIFEST_BYTES = 1_048_576;
    static final int SIGNATURE_BYTES = 64;
    private static final int MAXIMUM_WRITERS = 1024;
    private static final byte WRITER_ROLE = 0x01;
    private static final byte SNAPSHOT_ROLE = 0x02;
    private static final byte[] PAIR_DOMAIN = ascii("YCS-MIGRATION-PAIR/v1\0");
    private static final byte[] SIGNATURE_DOMAIN = ascii("YCS-MIGRATION-PAIR-SIGNATURE/v1\0");
    private static final byte[] SUBJECT_DOMAIN = ascii("YCS-MIGRATION-SUBJECT/v1\0");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern ENVIRONMENT = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern SCHEMA = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern KEY_REFERENCE = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,127}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern VERSION = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");
    private static final Set<String> SHARED_FIELDS = Set.of(
            "manifest_schema", "migration_set_id", "environment", "database_instance_fingerprint",
            "schema", "flyway_set_digest", "global_sequence", "signer_key_version");
    private static final Set<String> WRITER_FIELDS = union(SHARED_FIELDS,
            Set.of("issued_at", "expires_at", "writers"));
    private static final Set<String> SNAPSHOT_FIELDS = union(SHARED_FIELDS,
            Set.of("snapshot_id", "recovery_key_reference", "completed", "total_plaintext_bytes",
                    "total_envelope_bytes", "chunk_count", "chunks"));
    private static final Set<String> WRITER_ENTRY_FIELDS = Set.of(
            "artifact_id", "version", "source_digest", "migration_compatible");
    private static final Set<String> CHUNK_FIELDS = Set.of(
            "index", "final", "plaintext_size", "envelope_size", "sha256_digest");

    private final MigrationPreflightProperties properties;
    private final PairAdmissionStore admissionStore;
    private final Clock clock;
    private final ObjectMapper json;
    private final Map<String, ResolvedAnchor> anchors;
    private final SnapshotInventoryProof snapshotInventoryProof;

    public SignedMigrationManifestVerifier(
            MigrationPreflightProperties properties,
            PairAdmissionStore admissionStore,
            Clock clock) {
        this(properties, admissionStore, clock, bytes -> { });
    }

    public SignedMigrationManifestVerifier(
            MigrationPreflightProperties properties,
            PairAdmissionStore admissionStore,
            Clock clock,
            SnapshotInventoryProof snapshotInventoryProof) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.admissionStore = Objects.requireNonNull(admissionStore, "admissionStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.snapshotInventoryProof = Objects.requireNonNull(
                snapshotInventoryProof, "snapshotInventoryProof");
        this.json = strictMapper();
        this.anchors = resolveAnchors(properties.signerAnchors());
    }

    @Override
    public PairedAdmission verifyAndAdmit(PairedAdmissionRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            Optional<PairTuple> expectedPair = admissionStore.current();
            byte[] writerBytes = readCanonicalRegularFile(
                    request.writerManifest(), MAXIMUM_WRITER_MANIFEST_BYTES, "writer manifest");
            byte[] snapshotBytes = readCanonicalRegularFile(
                    request.snapshotManifest(), EncryptedSnapshotVerifier.MAXIMUM_MANIFEST_BYTES,
                    "snapshot manifest");
            byte[] writerSignature = readCanonicalRegularFile(
                    request.writerSignature(), SIGNATURE_BYTES, "writer signature");
            byte[] snapshotSignature = readCanonicalRegularFile(
                    request.snapshotSignature(), SIGNATURE_BYTES, "snapshot signature");
            if (writerSignature.length != SIGNATURE_BYTES || snapshotSignature.length != SIGNATURE_BYTES) {
                throw rejected(FailureCode.SIGNATURE_INVALID);
            }

            ParsedManifest writer = parseCanonical(writerBytes, ManifestRole.WRITER);
            ParsedManifest snapshot = parseCanonical(snapshotBytes, ManifestRole.SNAPSHOT);
            SharedSubject shared = requireSharedSubject(writer.root(), snapshot.root(), request.expectedSubject());
            ResolvedAnchor anchor = requireAnchor(shared.signerKeyVersion());
            Set<WriterIdentity> writers = validateWriter(writer.root());
            SnapshotInventory inventory = validateSnapshot(snapshot.root());

            byte[] writerDigest = sha256(writerBytes);
            byte[] snapshotDigest = sha256(snapshotBytes);
            byte[] pairDigest = pairDigest(shared, writerDigest, snapshotDigest);
            verifySignature(anchor.publicKey(), WRITER_ROLE, pairDigest, writerDigest, writerSignature);
            verifySignature(anchor.publicKey(), SNAPSHOT_ROLE, pairDigest, snapshotDigest, snapshotSignature);
            byte[] inventoryBytes = snapshotBytes.clone();
            try {
                snapshotInventoryProof.requireComplete(inventoryBytes);
            } catch (RuntimeException failure) {
                throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
            } finally {
                Arrays.fill(inventoryBytes, (byte) 0);
            }

            PairTuple tuple = new PairTuple(
                    shared.migrationSetId(), hex(subjectDigest(shared)), shared.globalSequence(),
                    shared.signerKeyVersion(), anchor.fingerprint(), hex(writerDigest),
                    hex(snapshotDigest), hex(pairDigest));
            AdmissionDecision decision = admissionStore.compareAndSet(
                    expectedPair, tuple, anchor.configuration());
            if (decision == AdmissionDecision.REJECTED) {
                throw rejected(FailureCode.REPLAY_OR_PAIR_CONFLICT);
            }
            Set<String> artifacts = new LinkedHashSet<>();
            writers.stream().map(WriterIdentity::artifactId).sorted().forEach(artifacts::add);
            return new PairedAdmission(
                    shared.globalSequence(), shared.signerKeyVersion(), hex(writerDigest),
                    hex(snapshotDigest), hex(pairDigest), artifacts, inventory.snapshotId(),
                    inventory.recoveryKeyReference());
        } catch (VerificationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw rejected(FailureCode.PAIR_ADMISSION_FAILED);
        }
    }

    @FunctionalInterface
    public interface SnapshotInventoryProof {
        /** Must completely validate the exact canonical snapshot bytes before pair CAS. */
        void requireComplete(byte[] canonicalSnapshotManifest);
    }

    private ParsedManifest parseCanonical(byte[] bytes, ManifestRole role) {
        try {
            JsonNode root = json.readTree(bytes);
            if (root == null || !root.isObject()) {
                throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
            }
            byte[] canonical = canonicalBytes(root);
            if (!MessageDigest.isEqual(bytes, canonical)) {
                throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
            }
            requireExactFields(root, role == ManifestRole.WRITER ? WRITER_FIELDS : SNAPSHOT_FIELDS);
            requireText(root, "manifest_schema",
                    role == ManifestRole.WRITER ? "ycs-writer-fence/v1" : "ycs-encrypted-snapshot/v1");
            return new ParsedManifest(root);
        } catch (IOException exception) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
    }

    private SharedSubject requireSharedSubject(
            JsonNode writer,
            JsonNode snapshot,
            DeploymentSubject expected) {
        SharedSubject writerSubject = sharedSubject(writer);
        SharedSubject snapshotSubject = sharedSubject(snapshot);
        if (!writerSubject.equals(snapshotSubject)) {
            throw rejected(FailureCode.SUBJECT_MISMATCH);
        }
        if (!writerSubject.migrationSetId().equals(expected.migrationSetId())
                || !writerSubject.environment().equals(expected.environment())
                || !writerSubject.databaseInstanceFingerprint().equals(expected.databaseInstanceFingerprint())
                || !writerSubject.schema().equals(expected.schema())
                || !writerSubject.flywaySetDigest().equals(expected.flywaySetDigest())) {
            throw rejected(FailureCode.SUBJECT_MISMATCH);
        }
        return writerSubject;
    }

    private static SharedSubject sharedSubject(JsonNode root) {
        String migrationSetId = requirePattern(root, "migration_set_id", ID);
        String environment = requirePattern(root, "environment", ENVIRONMENT);
        String database = requirePattern(root, "database_instance_fingerprint", SHA256);
        String schema = requirePattern(root, "schema", SCHEMA);
        String flyway = requirePattern(root, "flyway_set_digest", SHA256);
        long sequence = requireUnsignedLong(root, "global_sequence", Long.MAX_VALUE);
        String signer = requirePattern(root, "signer_key_version", VERSION);
        return new SharedSubject(migrationSetId, environment, database, schema, flyway, sequence, signer);
    }

    private Set<WriterIdentity> validateWriter(JsonNode root) {
        Instant issued = requireCanonicalInstant(root, "issued_at");
        Instant expires = requireCanonicalInstant(root, "expires_at");
        Instant now = clock.instant();
        if (issued.isAfter(now) || !expires.isAfter(now) || expires.isBefore(issued)) {
            throw rejected(FailureCode.WRITER_SET_INVALID);
        }
        JsonNode writersNode = root.get("writers");
        if (writersNode == null || !writersNode.isArray()
                || writersNode.isEmpty() || writersNode.size() > MAXIMUM_WRITERS) {
            throw rejected(FailureCode.WRITER_SET_INVALID);
        }
        Set<WriterIdentity> writers = new HashSet<>();
        Set<String> artifactIds = new HashSet<>();
        for (JsonNode entry : writersNode) {
            requireExactFields(entry, WRITER_ENTRY_FIELDS);
            WriterIdentity writer = new WriterIdentity(
                    requirePattern(entry, "artifact_id", ID),
                    requirePattern(entry, "version", VERSION),
                    requirePattern(entry, "source_digest", SHA256));
            if (!requireBoolean(entry, "migration_compatible")
                    || !artifactIds.add(writer.artifactId())
                    || !writers.add(writer)) {
                throw rejected(FailureCode.WRITER_SET_INVALID);
            }
        }
        if (!writers.equals(properties.compatibleWriters())) {
            throw rejected(FailureCode.WRITER_SET_INVALID);
        }
        return Set.copyOf(writers);
    }

    private SnapshotInventory validateSnapshot(JsonNode root) {
        String snapshotId = requirePattern(root, "snapshot_id", ID);
        String recoveryKey = requirePattern(root, "recovery_key_reference", KEY_REFERENCE);
        if (!properties.recoveryKeyReferences().contains(recoveryKey)
                || !requireBoolean(root, "completed")) {
            throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
        }
        long expectedPlaintext = requireUnsignedLong(
                root, "total_plaintext_bytes", EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES);
        long expectedEnvelope = requireUnsignedLong(
                root, "total_envelope_bytes", EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES);
        long chunkCount = requireUnsignedLong(
                root, "chunk_count", EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT);
        JsonNode chunks = root.get("chunks");
        if (chunks == null || !chunks.isArray() || chunks.isEmpty()
                || chunks.size() != chunkCount
                || chunks.size() > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT) {
            throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
        }
        long plaintext = 0;
        long envelope = 0;
        for (int index = 0; index < chunks.size(); index++) {
            JsonNode chunk = chunks.get(index);
            requireExactFields(chunk, CHUNK_FIELDS);
            if (requireUnsignedLong(chunk, "index", EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT - 1L)
                    != index) {
                throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
            }
            boolean terminal = requireBoolean(chunk, "final");
            if (terminal != (index == chunks.size() - 1)) {
                throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
            }
            long chunkPlaintext = requireUnsignedLong(
                    chunk, "plaintext_size", EncryptedSnapshotVerifier.MAXIMUM_CHUNK_PLAINTEXT_BYTES);
            long chunkEnvelope = requireUnsignedLong(
                    chunk, "envelope_size", EncryptedSnapshotVerifier.MAXIMUM_CHUNK_ENVELOPE_BYTES);
            requirePattern(chunk, "sha256_digest", SHA256);
            if (chunkPlaintext == 0 || chunkEnvelope <= chunkPlaintext) {
                throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
            }
            try {
                plaintext = Math.addExact(plaintext, chunkPlaintext);
                envelope = Math.addExact(envelope, chunkEnvelope);
            } catch (ArithmeticException exception) {
                throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
            }
            if (plaintext > EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES
                    || envelope > EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES) {
                throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
            }
        }
        if (plaintext != expectedPlaintext || envelope != expectedEnvelope) {
            throw rejected(FailureCode.SNAPSHOT_INVENTORY_INVALID);
        }
        return new SnapshotInventory(snapshotId, recoveryKey);
    }

    private ResolvedAnchor requireAnchor(String version) {
        ResolvedAnchor anchor = anchors.get(version);
        if (anchor == null || anchor.configuration().state() == AnchorState.RETIRED
                || anchor.configuration().state() == AnchorState.REVOKED) {
            throw rejected(FailureCode.SIGNER_NOT_TRUSTED);
        }
        return anchor;
    }

    private static Map<String, ResolvedAnchor> resolveAnchors(List<SignerAnchor> configured) {
        Map<String, ResolvedAnchor> resolved = new HashMap<>();
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            for (SignerAnchor anchor : configured) {
                byte[] encoded = Base64.getDecoder().decode(anchor.x509PublicKeyBase64());
                PublicKey key = factory.generatePublic(new X509EncodedKeySpec(encoded));
                String actualFingerprint = hex(sha256(encoded));
                if (!actualFingerprint.equals(anchor.fingerprint())) {
                    throw new IllegalArgumentException("migration signer fingerprint drift");
                }
                resolved.put(anchor.version(), new ResolvedAnchor(anchor, key, actualFingerprint));
            }
            return Map.copyOf(resolved);
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid migration signer trust configuration");
        }
    }

    private static void verifySignature(
            PublicKey publicKey,
            byte role,
            byte[] pairDigest,
            byte[] roleDigest,
            byte[] detachedSignature) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(signaturePayload(role, pairDigest, roleDigest));
            if (!verifier.verify(detachedSignature)) {
                throw rejected(FailureCode.SIGNATURE_INVALID);
            }
        } catch (VerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw rejected(FailureCode.SIGNATURE_INVALID);
        }
    }

    static byte[] pairDigest(SharedSubject subject, byte[] writerDigest, byte[] snapshotDigest) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(PAIR_DOMAIN);
        writeLengthPrefixed(output, subject.migrationSetId());
        writeLengthPrefixed(output, subject.environment());
        writeLengthPrefixed(output, subject.databaseInstanceFingerprint());
        writeLengthPrefixed(output, subject.schema());
        writeLengthPrefixed(output, subject.flywaySetDigest());
        output.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(subject.globalSequence()).array());
        writeLengthPrefixed(output, subject.signerKeyVersion());
        output.writeBytes(writerDigest);
        output.writeBytes(snapshotDigest);
        return sha256(output.toByteArray());
    }

    static byte[] signaturePayload(byte role, byte[] pairDigest, byte[] roleDigest) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(SIGNATURE_DOMAIN);
        output.write(role);
        output.writeBytes(pairDigest);
        output.writeBytes(roleDigest);
        return output.toByteArray();
    }

    static byte[] canonicalBytes(JsonNode node) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeCanonical(node, output, strictMapper());
        return output.toByteArray();
    }

    private static void writeCanonical(JsonNode node, ByteArrayOutputStream output, ObjectMapper mapper) {
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
                    output.writeBytes(mapper.writeValueAsBytes(names.get(index)));
                    output.write(':');
                    writeCanonical(node.get(names.get(index)), output, mapper);
                }
                output.write('}');
            } else if (node.isArray()) {
                output.write('[');
                for (int index = 0; index < node.size(); index++) {
                    if (index > 0) {
                        output.write(',');
                    }
                    writeCanonical(node.get(index), output, mapper);
                }
                output.write(']');
            } else if (node.isTextual()) {
                output.writeBytes(mapper.writeValueAsBytes(node.textValue()));
            } else if (node.isBoolean()) {
                output.writeBytes(ascii(Boolean.toString(node.booleanValue())));
            } else if (node.isIntegralNumber()) {
                output.writeBytes(ascii(node.bigIntegerValue().toString()));
            } else {
                throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
            }
        } catch (IOException exception) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
    }

    private static ObjectMapper strictMapper() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        return new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private static byte[] readCanonicalRegularFile(Path path, int maximumBytes, String role) {
        try {
            if (!path.isAbsolute() || !path.equals(path.normalize()) || Files.isSymbolicLink(path)) {
                throw rejected(FailureCode.PATH_INVALID);
            }
            Path real = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!path.equals(real)) {
                throw rejected(FailureCode.PATH_INVALID);
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() <= 0 || attributes.size() > maximumBytes) {
                throw rejected(FailureCode.PATH_INVALID);
            }
            byte[] bytes = new byte[Math.toIntExact(attributes.size())];
            try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read < 0) {
                        throw rejected(FailureCode.PATH_INVALID);
                    }
                    offset += read;
                }
                if (input.read() != -1) {
                    throw rejected(FailureCode.PATH_INVALID);
                }
            }
            return bytes;
        } catch (VerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VerificationException(FailureCode.PATH_INVALID, role);
        }
    }

    private static byte[] subjectDigest(SharedSubject subject) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(SUBJECT_DOMAIN);
        writeLengthPrefixed(output, subject.environment());
        writeLengthPrefixed(output, subject.databaseInstanceFingerprint());
        writeLengthPrefixed(output, subject.schema());
        writeLengthPrefixed(output, subject.flywaySetDigest());
        return sha256(output.toByteArray());
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        output.writeBytes(bytes);
    }

    private static Instant requireCanonicalInstant(JsonNode root, String field) {
        String value = requireText(root, field);
        try {
            Instant parsed = Instant.parse(value);
            if (!parsed.toString().equals(value)) {
                throw rejected(FailureCode.WRITER_SET_INVALID);
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw rejected(FailureCode.WRITER_SET_INVALID);
        }
    }

    private static void requireExactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
    }

    private static String requireText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
        return value.textValue();
    }

    private static void requireText(JsonNode root, String field, String exact) {
        if (!exact.equals(requireText(root, field))) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
    }

    private static String requirePattern(JsonNode root, String field, Pattern pattern) {
        String value = requireText(root, field);
        if (!pattern.matcher(value).matches()) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
        return value;
    }

    private static boolean requireBoolean(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isBoolean()) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
        return value.booleanValue();
    }

    private static long requireUnsignedLong(JsonNode root, String field, long maximum) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
        long number = value.longValue();
        if (number < 0 || number > maximum) {
            throw rejected(FailureCode.CANONICAL_SCHEMA_INVALID);
        }
        return number;
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static VerificationException rejected(FailureCode code) {
        return new VerificationException(code, "migration manifest pair rejected");
    }

    public enum AdmissionDecision {
        INSERTED,
        ADVANCED,
        IDEMPOTENT,
        REJECTED
    }

    public interface PairAdmissionStore {
        Optional<PairTuple> current();

        AdmissionDecision compareAndSet(
                Optional<PairTuple> expected,
                PairTuple candidate,
                SignerAnchor signer);
    }

    /** Immutable persisted singleton value; digests and fingerprint are lowercase SHA-256 hex. */
    public record PairTuple(
            String migrationSetId,
            String subjectDigest,
            long globalSequence,
            String signerKeyVersion,
            String signerFingerprint,
            String writerDigest,
            String snapshotDigest,
            String pairDigest) {

        public PairTuple {
            if (globalSequence < 0) {
                throw new IllegalArgumentException("globalSequence must be unsigned");
            }
            requireValue(ID, migrationSetId, "migrationSetId");
            requireValue(SHA256, subjectDigest, "subjectDigest");
            requireValue(VERSION, signerKeyVersion, "signerKeyVersion");
            requireValue(SHA256, signerFingerprint, "signerFingerprint");
            requireValue(SHA256, writerDigest, "writerDigest");
            requireValue(SHA256, snapshotDigest, "snapshotDigest");
            requireValue(SHA256, pairDigest, "pairDigest");
        }

        private static void requireValue(Pattern pattern, String value, String field) {
            if (value == null || !pattern.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " is not canonical");
            }
        }
    }

    /** Thread-safe deterministic store useful for non-Spring deployments and exhaustive tests. */
    public static final class InMemoryPairAdmissionStore implements PairAdmissionStore {
        private PairTuple current;
        private long writes;

        @Override
        public synchronized AdmissionDecision compareAndSet(
                Optional<PairTuple> expected,
                PairTuple candidate,
                SignerAnchor signer) {
            AdmissionDecision decision = decide(expected.orElse(null), current, candidate, signer);
            if (decision == AdmissionDecision.INSERTED || decision == AdmissionDecision.ADVANCED) {
                current = candidate;
                writes++;
            }
            return decision;
        }

        @Override
        public synchronized Optional<PairTuple> current() {
            return Optional.ofNullable(current);
        }

        public synchronized long writes() {
            return writes;
        }
    }

    /** Transactional MySQL singleton CAS over {@code ycs_crypto_manifest_pair_admission}. */
    public static final class JdbcPairAdmissionStore implements PairAdmissionStore {
        private static final String SELECT = "SELECT migration_set_id, HEX(canonical_subject_digest), "
                + "global_sequence, signer_key_version, HEX(signer_fingerprint), HEX(writer_digest), "
                + "HEX(snapshot_digest), HEX(pair_digest), optimistic_version "
                + "FROM ycs_crypto_manifest_pair_admission WHERE singleton_id = 1";
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;

        public JdbcPairAdmissionStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.transactions = Objects.requireNonNull(transactions, "transactions");
        }

        @Override
        public Optional<PairTuple> current() {
            List<StoredPair> rows = load("");
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().tuple());
        }

        @Override
        public AdmissionDecision compareAndSet(
                Optional<PairTuple> expected,
                PairTuple candidate,
                SignerAnchor signer) {
            DataAccessException lastFailure = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    AdmissionDecision decision = transactions.execute(
                            status -> lockedDecision(expected, candidate, signer));
                    if (decision == null) {
                        throw new IllegalStateException("pair admission transaction returned no decision");
                    }
                    return decision;
                } catch (DataAccessException exception) {
                    lastFailure = exception;
                }
            }
            throw new IllegalStateException("atomic migration pair admission failed", lastFailure);
        }

        private AdmissionDecision lockedDecision(
                Optional<PairTuple> expected,
                PairTuple candidate,
                SignerAnchor signer) {
            List<StoredPair> rows = load(" FOR UPDATE");
            PairTuple current = rows.isEmpty() ? null : rows.getFirst().tuple();
            AdmissionDecision decision = decide(expected.orElse(null), current, candidate, signer);
            if (decision == AdmissionDecision.INSERTED) {
                jdbc.update("INSERT INTO ycs_crypto_manifest_pair_admission "
                                + "(singleton_id, migration_set_id, canonical_subject_digest, global_sequence, "
                                + "signer_key_version, signer_fingerprint, writer_digest, snapshot_digest, pair_digest) "
                                + "VALUES (1, ?, UNHEX(?), ?, ?, UNHEX(?), UNHEX(?), UNHEX(?), UNHEX(?))",
                        candidate.migrationSetId(), candidate.subjectDigest(), candidate.globalSequence(),
                        candidate.signerKeyVersion(), candidate.signerFingerprint(), candidate.writerDigest(),
                        candidate.snapshotDigest(), candidate.pairDigest());
            } else if (decision == AdmissionDecision.ADVANCED) {
                int updated = jdbc.update("UPDATE ycs_crypto_manifest_pair_admission SET migration_set_id = ?, "
                                + "canonical_subject_digest = UNHEX(?), global_sequence = ?, signer_key_version = ?, "
                                + "signer_fingerprint = UNHEX(?), writer_digest = UNHEX(?), "
                                + "snapshot_digest = UNHEX(?), pair_digest = UNHEX(?), "
                                + "optimistic_version = optimistic_version + 1 WHERE singleton_id = 1 "
                                + "AND optimistic_version = ?",
                        candidate.migrationSetId(), candidate.subjectDigest(), candidate.globalSequence(),
                        candidate.signerKeyVersion(), candidate.signerFingerprint(), candidate.writerDigest(),
                        candidate.snapshotDigest(), candidate.pairDigest(), rows.getFirst().optimisticVersion());
                if (updated != 1) {
                    throw new IllegalStateException("migration pair CAS lost without a decision");
                }
            }
            return decision;
        }

        private List<StoredPair> load(String lockingSuffix) {
            return jdbc.query(SELECT + lockingSuffix, (resultSet, rowNumber) -> new StoredPair(
                    new PairTuple(
                            resultSet.getString(1), resultSet.getString(2).toLowerCase(),
                            resultSet.getLong(3), resultSet.getString(4),
                            resultSet.getString(5).toLowerCase(), resultSet.getString(6).toLowerCase(),
                            resultSet.getString(7).toLowerCase(), resultSet.getString(8).toLowerCase()),
                    resultSet.getLong(9)));
        }
    }

    private static AdmissionDecision decide(
            PairTuple expected,
            PairTuple current,
            PairTuple candidate,
            SignerAnchor signer) {
        if (signer.state() == AnchorState.RETIRED || signer.state() == AnchorState.REVOKED) {
            return AdmissionDecision.REJECTED;
        }
        if (current == null) {
            return expected == null && signer.state() == AnchorState.ACTIVE
                    ? AdmissionDecision.INSERTED : AdmissionDecision.REJECTED;
        }
        if (current.equals(candidate)) {
            if (signer.state() == AnchorState.ACTIVE) {
                return AdmissionDecision.IDEMPOTENT;
            }
            return signer.state() == AnchorState.RETIRING
                    && candidate.globalSequence() <= Objects.requireNonNull(signer.maxSequence())
                    ? AdmissionDecision.IDEMPOTENT : AdmissionDecision.REJECTED;
        }
        if (!Objects.equals(expected, current)) {
            return AdmissionDecision.REJECTED;
        }
        if (signer.state() == AnchorState.ACTIVE
                && Long.compareUnsigned(candidate.globalSequence(), current.globalSequence()) > 0) {
            return AdmissionDecision.ADVANCED;
        }
        return AdmissionDecision.REJECTED;
    }

    public enum FailureCode {
        PATH_INVALID,
        CANONICAL_SCHEMA_INVALID,
        SUBJECT_MISMATCH,
        WRITER_SET_INVALID,
        SNAPSHOT_INVENTORY_INVALID,
        SIGNER_NOT_TRUSTED,
        SIGNATURE_INVALID,
        REPLAY_OR_PAIR_CONFLICT,
        PAIR_ADMISSION_FAILED
    }

    public static final class VerificationException extends IllegalStateException {
        private final FailureCode code;

        private VerificationException(FailureCode code, String message) {
            super(message);
            this.code = code;
        }

        public FailureCode code() {
            return code;
        }
    }

    record SharedSubject(
            String migrationSetId,
            String environment,
            String databaseInstanceFingerprint,
            String schema,
            String flywaySetDigest,
            long globalSequence,
            String signerKeyVersion) {
    }

    private record ParsedManifest(JsonNode root) {
    }

    private record SnapshotInventory(String snapshotId, String recoveryKeyReference) {
    }

    private record ResolvedAnchor(SignerAnchor configuration, PublicKey publicKey, String fingerprint) {
    }

    private record StoredPair(PairTuple tuple, long optimisticVersion) {
    }

    private enum ManifestRole {
        WRITER,
        SNAPSHOT
    }
}
