package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.AnchorState;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.SignerAnchor;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.WriterIdentity;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.FailureCode;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.InMemoryPairAdmissionStore;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.PairAdmissionStore;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.PairTuple;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.VerificationException;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.DeploymentSubject;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmissionRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedMigrationManifestVerifierTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String DATABASE = "1".repeat(64);
    private static final String FLYWAY = "2".repeat(64);
    private static final String WRITER_DIGEST = "3".repeat(64);
    private static final String CHUNK_0_DIGEST = "4".repeat(64);
    private static final String CHUNK_1_DIGEST = "5".repeat(64);
    private static final WriterIdentity WRITER =
            new WriterIdentity("core-writer", "1.0.0", WRITER_DIGEST);
    private static final DeploymentSubject SUBJECT =
            new DeploymentSubject("migration-set-a", "test", DATABASE, "ycs_sms", FLYWAY);

    @TempDir
    Path temporaryDirectory;

    private KeyPair oldKey;
    private KeyPair newKey;
    private AtomicInteger files;

    @BeforeEach
    void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        oldKey = generator.generateKeyPair();
        newKey = generator.generateKeyPair();
        files = new AtomicInteger();
    }

    @Test
    void schemasAreClosedAndCarryTheExactSnapshotBounds() throws Exception {
        JsonNode writer = JSON.readTree(Path.of(
                "src/main/resources/security/migration/ycs-writer-fence-v1.schema.json").toFile());
        JsonNode snapshot = JSON.readTree(Path.of(
                "src/main/resources/security/migration/ycs-encrypted-snapshot-v1.schema.json").toFile());

        assertClosed(writer);
        assertClosed(writer.at("/properties/writers/items"));
        assertClosed(snapshot);
        assertClosed(snapshot.at("/properties/chunks/items"));
        assertThat(snapshot.at("/properties/chunks/maxItems").asInt()).isEqualTo(104_858);
        assertThat(snapshot.at("/properties/chunks/items/properties/plaintext_size/maximum").asLong())
                .isEqualTo(10_485_760L);
        assertThat(snapshot.at("/properties/chunks/items/properties/envelope_size/maximum").asLong())
                .isEqualTo(10_485_905L);
        assertThat(snapshot.at("/properties/total_plaintext_bytes/maximum").asLong())
                .isEqualTo(1_099_511_627_776L);
        assertThat(snapshot.at("/properties/total_envelope_bytes/maximum").asLong())
                .isEqualTo(1_099_526_832_186L);
        assertThat(EncryptedSnapshotVerifier.MAXIMUM_MANIFEST_BYTES).isEqualTo(33_554_432);
    }

    @Test
    void canonicalRoleSeparatedPairSignaturesAdmitOnceAndReverifyIdempotently() throws Exception {
        InMemoryPairAdmissionStore store = new InMemoryPairAdmissionStore();
        SignedMigrationManifestVerifier verifier = verifier(activeOnly(oldKey, "signer-v1"), store);
        PairFiles pair = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });

        WriterFencePort.PairedAdmission first = verifier.verifyAndAdmit(pair.request());
        WriterFencePort.PairedAdmission second = verifier.verifyAndAdmit(pair.request());

        assertThat(first).isEqualTo(second);
        assertThat(first.compatibleWriterArtifacts()).containsExactly("core-writer");
        assertThat(first.writerDigest()).hasSize(64);
        assertThat(first.snapshotDigest()).hasSize(64);
        assertThat(first.pairDigest()).hasSize(64);
        assertThat(store.writes()).isOne();

        byte[] writerPayload = SignedMigrationManifestVerifier.signaturePayload(
                (byte) 0x01, HexFormat.of().parseHex(first.pairDigest()),
                HexFormat.of().parseHex(first.writerDigest()));
        byte[] snapshotPayload = SignedMigrationManifestVerifier.signaturePayload(
                (byte) 0x02, HexFormat.of().parseHex(first.pairDigest()),
                HexFormat.of().parseHex(first.snapshotDigest()));
        assertThat(writerPayload).isNotEqualTo(snapshotPayload);
        assertThat(new String(writerPayload, 0, 32, StandardCharsets.US_ASCII))
                .startsWith("YCS-MIGRATION-PAIR-SIGNATURE/v1");
    }

    @Test
    void rejectsNoncanonicalDuplicateUnknownAndNonregularInputsBeforeCas() throws Exception {
        InMemoryPairAdmissionStore store = new InMemoryPairAdmissionStore();
        SignedMigrationManifestVerifier verifier = verifier(activeOnly(oldKey, "signer-v1"), store);

        PairFiles whitespace = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });
        Files.writeString(whitespace.writerManifest(),
                Files.readString(whitespace.writerManifest()) + "\n", StandardCharsets.UTF_8);
        assertRejected(verifier, whitespace.request(), FailureCode.CANONICAL_SCHEMA_INVALID);

        PairFiles unknown = writePair(1, "signer-v1", oldKey,
                node -> node.put("unknown", true), node -> { });
        assertRejected(verifier, unknown.request(), FailureCode.CANONICAL_SCHEMA_INVALID);

        PairFiles duplicate = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });
        String duplicateJson = Files.readString(duplicate.writerManifest()).replaceFirst(
                "\\{", "{\"environment\":\"test\",");
        Files.writeString(duplicate.writerManifest(), duplicateJson, StandardCharsets.UTF_8);
        assertRejected(verifier, duplicate.request(), FailureCode.CANONICAL_SCHEMA_INVALID);

        PairFiles relative = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });
        PairedAdmissionRequest relativeRequest = new PairedAdmissionRequest(
                Path.of("writer.json"), relative.writerSignature(), relative.snapshotManifest(),
                relative.snapshotSignature(), SUBJECT);
        assertRejected(verifier, relativeRequest, FailureCode.PATH_INVALID);

        Path directory = temporaryDirectory.resolve("not-a-file");
        Files.createDirectory(directory);
        PairFiles nonregular = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });
        assertRejected(verifier, new PairedAdmissionRequest(
                directory, nonregular.writerSignature(), nonregular.snapshotManifest(),
                nonregular.snapshotSignature(), SUBJECT), FailureCode.PATH_INVALID);
        assertThat(store.writes()).isZero();
    }

    @Test
    void rejectsEveryChunkInventoryFaultBeforeCas() throws Exception {
        List<NamedMutation> faults = List.of(
                new NamedMutation("missing", snapshot -> {
                    ((ArrayNode) snapshot.get("chunks")).remove(0);
                }),
                new NamedMutation("duplicate", snapshot -> {
                    ((ObjectNode) snapshot.at("/chunks/1")).put("index", 0);
                }),
                new NamedMutation("reordered", snapshot -> {
                    ArrayNode chunks = (ArrayNode) snapshot.get("chunks");
                    JsonNode first = chunks.get(0).deepCopy();
                    chunks.set(0, chunks.get(1).deepCopy());
                    chunks.set(1, first);
                }),
                new NamedMutation("truncated", snapshot -> {
                    ((ArrayNode) snapshot.get("chunks")).remove(1);
                }),
                new NamedMutation("extra", snapshot -> {
                    ((ArrayNode) snapshot.get("chunks")).add(snapshot.at("/chunks/1").deepCopy());
                }),
                new NamedMutation("post-final", snapshot -> {
                    ((ObjectNode) snapshot.at("/chunks/0")).put("final", true);
                }),
                new NamedMutation("chunk-size", FailureCode.CANONICAL_SCHEMA_INVALID, snapshot -> {
                    ((ObjectNode) snapshot.at("/chunks/0")).put("plaintext_size", 10_485_761L);
                }),
                new NamedMutation("envelope-size", FailureCode.CANONICAL_SCHEMA_INVALID, snapshot -> {
                    ((ObjectNode) snapshot.at("/chunks/0")).put("envelope_size", 10_485_906L);
                }),
                new NamedMutation("total-mismatch", snapshot -> {
                    snapshot.put("total_plaintext_bytes", 201);
                }));

        for (NamedMutation fault : faults) {
            InMemoryPairAdmissionStore store = new InMemoryPairAdmissionStore();
            PairFiles pair = writePair(1, "signer-v1", oldKey, node -> { }, fault.mutation());
            assertThatThrownBy(() -> verifier(activeOnly(oldKey, "signer-v1"), store)
                    .verifyAndAdmit(pair.request()))
                    .as(fault.name())
                    .isInstanceOfSatisfying(VerificationException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo(fault.expectedCode()))
                    .hasMessage("migration manifest pair rejected");
            assertThat(store.writes()).as(fault.name()).isZero();
        }
    }

    @Test
    void rejectsSubjectSignerAndCrossPairSpliceFaultsWithoutAdmission() throws Exception {
        InMemoryPairAdmissionStore store = new InMemoryPairAdmissionStore();
        SignedMigrationManifestVerifier verifier = verifier(activeOnly(oldKey, "signer-v1"), store);

        PairFiles subjectMismatch = writePair(1, "signer-v1", oldKey,
                node -> node.put("environment", "other"), node -> node.put("environment", "other"));
        assertRejected(verifier, subjectMismatch.request(), FailureCode.SUBJECT_MISMATCH);

        PairFiles roleMismatch = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });
        Files.write(roleMismatch.writerSignature(), Files.readAllBytes(roleMismatch.snapshotSignature()));
        assertRejected(verifier, roleMismatch.request(), FailureCode.SIGNATURE_INVALID);

        PairFiles left = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });
        PairFiles right = writePair(1, "signer-v1", oldKey, node -> { },
                node -> node.put("snapshot_id", "snapshot-b"));
        assertRejected(verifier, new PairedAdmissionRequest(
                left.writerManifest(), left.writerSignature(), right.snapshotManifest(),
                right.snapshotSignature(), SUBJECT), FailureCode.SIGNATURE_INVALID);

        PairFiles unknown = writePair(1, "unknown", newKey, node -> { }, node -> { });
        assertRejected(verifier, unknown.request(), FailureCode.SIGNER_NOT_TRUSTED);
        assertThat(store.writes()).isZero();
    }

    @Test
    void sameSequenceChangeAndIndividualReplayCannotReplaceAcceptedTuple() throws Exception {
        InMemoryPairAdmissionStore store = new InMemoryPairAdmissionStore();
        SignedMigrationManifestVerifier verifier = verifier(activeOnly(oldKey, "signer-v1"), store);
        PairFiles accepted = writePair(4, "signer-v1", oldKey, node -> { }, node -> { });
        verifier.verifyAndAdmit(accepted.request());
        PairTuple tuple = store.current().orElseThrow();

        PairFiles changedWriter = writePair(4, "signer-v1", oldKey,
                node -> ((ObjectNode) node.at("/writers/0")).put("version", "1.0.1"), node -> { });
        assertRejected(verifier, changedWriter.request(), FailureCode.WRITER_SET_INVALID);

        PairFiles changedSnapshot = writePair(4, "signer-v1", oldKey, node -> { },
                node -> node.put("snapshot_id", "snapshot-changed"));
        assertRejected(verifier, changedSnapshot.request(), FailureCode.REPLAY_OR_PAIR_CONFLICT);

        PairFiles next = writePair(5, "signer-v1", oldKey, node -> { },
                node -> node.put("snapshot_id", "snapshot-next"));
        assertRejected(verifier, new PairedAdmissionRequest(
                accepted.writerManifest(), accepted.writerSignature(), next.snapshotManifest(),
                next.snapshotSignature(), SUBJECT), FailureCode.SUBJECT_MISMATCH);
        assertThat(store.current()).contains(tuple);
        assertThat(store.writes()).isOne();
    }

    @Test
    void trustRolloutAllowsOnlyExactRetiringTupleThenRecoversFromRevocation() throws Exception {
        InMemoryPairAdmissionStore store = new InMemoryPairAdmissionStore();
        PairFiles oldPair = writePair(10, "signer-v1", oldKey, node -> { }, node -> { });
        verifier(activeOnly(oldKey, "signer-v1"), store).verifyAndAdmit(oldPair.request());

        MigrationPreflightProperties rollout = properties(List.of(
                anchor(oldKey, "signer-v1", AnchorState.RETIRING, 10L),
                anchor(newKey, "signer-v2", AnchorState.ACTIVE, null)));
        SignedMigrationManifestVerifier rolloutVerifier = verifier(rollout, store);
        rolloutVerifier.verifyAndAdmit(oldPair.request());

        PairFiles oldHigher = writePair(11, "signer-v1", oldKey, node -> { }, node -> { });
        assertRejected(rolloutVerifier, oldHigher.request(), FailureCode.REPLAY_OR_PAIR_CONFLICT);
        PairFiles newPair = writePair(11, "signer-v2", newKey, node -> { },
                node -> node.put("snapshot_id", "snapshot-v2"));
        rolloutVerifier.verifyAndAdmit(newPair.request());

        MigrationPreflightProperties removed = activeOnly(newKey, "signer-v2");
        assertRejected(verifier(removed, store), oldPair.request(), FailureCode.SIGNER_NOT_TRUSTED);

        MigrationPreflightProperties compromised = properties(List.of(
                anchor(oldKey, "signer-v1", AnchorState.REVOKED, null),
                anchor(newKey, "signer-v2", AnchorState.ACTIVE, null)));
        InMemoryPairAdmissionStore compromisedStore = new InMemoryPairAdmissionStore();
        verifier(activeOnly(oldKey, "signer-v1"), compromisedStore).verifyAndAdmit(oldPair.request());
        assertRejected(verifier(compromised, compromisedStore), oldPair.request(),
                FailureCode.SIGNER_NOT_TRUSTED);
        PairFiles recovery = writePair(12, "signer-v2", newKey, node -> { },
                node -> node.put("snapshot_id", "snapshot-recovery"));
        verifier(compromised, compromisedStore).verifyAndAdmit(recovery.request());
        assertThat(compromisedStore.current().orElseThrow().signerKeyVersion()).isEqualTo("signer-v2");
    }

    @Test
    void invalidTrustTopologyAndFingerprintDriftFailAtConstruction() {
        assertThatThrownBy(() -> properties(List.of(
                anchor(oldKey, "signer-v1", AnchorState.ACTIVE, null),
                anchor(newKey, "signer-v2", AnchorState.ACTIVE, null))))
                .isInstanceOf(IllegalArgumentException.class);

        SignerAnchor drifted = new SignerAnchor(
                "signer-v1", AnchorState.ACTIVE, "0".repeat(64),
                Base64.getEncoder().encodeToString(oldKey.getPublic().getEncoded()), null);
        assertThatThrownBy(() -> verifier(properties(List.of(drifted)), new InMemoryPairAdmissionStore()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentIdenticalAdmissionsInsertOnceAndRemainExact() throws Exception {
        InMemoryPairAdmissionStore store = new InMemoryPairAdmissionStore();
        SignedMigrationManifestVerifier verifier = verifier(activeOnly(oldKey, "signer-v1"), store);
        PairFiles pair = writePair(1, "signer-v1", oldKey, node -> { }, node -> { });
        List<Callable<String>> calls = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            calls.add(() -> verifier.verifyAndAdmit(pair.request()).pairDigest());
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<String>> results = executor.invokeAll(calls);
            Set<String> digests = new java.util.HashSet<>();
            for (Future<String> result : results) {
                digests.add(result.get());
            }
            assertThat(digests).hasSize(1);
        }
        assertThat(store.writes()).isOne();
        assertThat(store.current()).isPresent();
    }

    @Test
    void concurrentDifferentHigherPairsHaveExactlyOneWinnerAndNoHalfPair() throws Exception {
        InMemoryPairAdmissionStore delegate = new InMemoryPairAdmissionStore();
        SignedMigrationManifestVerifier baseVerifier = verifier(activeOnly(oldKey, "signer-v1"), delegate);
        baseVerifier.verifyAndAdmit(writePair(1, "signer-v1", oldKey, node -> { }, node -> { }).request());
        BarrierStore barrierStore = new BarrierStore(delegate);
        SignedMigrationManifestVerifier verifier = verifier(activeOnly(oldKey, "signer-v1"), barrierStore);
        PairFiles second = writePair(2, "signer-v1", oldKey, node -> { },
                node -> node.put("snapshot_id", "snapshot-second"));
        PairFiles third = writePair(3, "signer-v1", oldKey, node -> { },
                node -> node.put("snapshot_id", "snapshot-third"));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> admitted(verifier, second.request()));
            Future<Boolean> other = executor.submit(() -> admitted(verifier, third.request()));
            assertThat(List.of(first.get(), other.get()).stream().filter(Boolean::booleanValue).count()).isOne();
        }
        PairTuple accepted = delegate.current().orElseThrow();
        assertThat(accepted.globalSequence()).isIn(2L, 3L);
        assertThat(accepted.writerDigest()).hasSize(64);
        assertThat(accepted.snapshotDigest()).hasSize(64);
        assertThat(accepted.pairDigest()).hasSize(64);
        assertThat(delegate.writes()).isEqualTo(2);
    }

    private PairFiles writePair(
            long sequence,
            String signerVersion,
            KeyPair signingKey,
            Consumer<ObjectNode> writerMutation,
            Consumer<ObjectNode> snapshotMutation) throws Exception {
        ObjectNode writer = writer(sequence, signerVersion);
        ObjectNode snapshot = snapshot(sequence, signerVersion);
        writerMutation.accept(writer);
        snapshotMutation.accept(snapshot);
        byte[] writerBytes = SignedMigrationManifestVerifier.canonicalBytes(writer);
        byte[] snapshotBytes = SignedMigrationManifestVerifier.canonicalBytes(snapshot);
        byte[] writerDigest = sha256(writerBytes);
        byte[] snapshotDigest = sha256(snapshotBytes);
        SignedMigrationManifestVerifier.SharedSubject subject =
                new SignedMigrationManifestVerifier.SharedSubject(
                        text(writer, "migration_set_id"), text(writer, "environment"),
                        text(writer, "database_instance_fingerprint"), text(writer, "schema"),
                        text(writer, "flyway_set_digest"), writer.path("global_sequence").asLong(), signerVersion);
        byte[] pairDigest = SignedMigrationManifestVerifier.pairDigest(subject, writerDigest, snapshotDigest);
        byte[] writerSignature = sign(signingKey,
                SignedMigrationManifestVerifier.signaturePayload((byte) 0x01, pairDigest, writerDigest));
        byte[] snapshotSignature = sign(signingKey,
                SignedMigrationManifestVerifier.signaturePayload((byte) 0x02, pairDigest, snapshotDigest));
        String prefix = "pair-" + files.incrementAndGet();
        Path writerPath = temporaryDirectory.resolve(prefix + "-writer.json");
        Path writerSignaturePath = temporaryDirectory.resolve(prefix + "-writer.sig");
        Path snapshotPath = temporaryDirectory.resolve(prefix + "-snapshot.json");
        Path snapshotSignaturePath = temporaryDirectory.resolve(prefix + "-snapshot.sig");
        Files.write(writerPath, writerBytes);
        Files.write(writerSignaturePath, writerSignature);
        Files.write(snapshotPath, snapshotBytes);
        Files.write(snapshotSignaturePath, snapshotSignature);
        return new PairFiles(writerPath, writerSignaturePath, snapshotPath, snapshotSignaturePath);
    }

    private static ObjectNode writer(long sequence, String signerVersion) {
        ObjectNode root = JSON.createObjectNode();
        shared(root, "ycs-writer-fence/v1", sequence, signerVersion);
        root.put("issued_at", "2026-08-31T23:00:00Z");
        root.put("expires_at", "2026-09-02T00:00:00Z");
        ObjectNode writer = root.putArray("writers").addObject();
        writer.put("artifact_id", WRITER.artifactId());
        writer.put("version", WRITER.version());
        writer.put("source_digest", WRITER.sourceDigest());
        writer.put("migration_compatible", true);
        return root;
    }

    private static ObjectNode snapshot(long sequence, String signerVersion) {
        ObjectNode root = JSON.createObjectNode();
        shared(root, "ycs-encrypted-snapshot/v1", sequence, signerVersion);
        root.put("snapshot_id", "snapshot-a");
        root.put("recovery_key_reference", "snapshot-recovery.v1");
        root.put("completed", true);
        root.put("total_plaintext_bytes", 200);
        root.put("total_envelope_bytes", 490);
        root.put("chunk_count", 2);
        ArrayNode chunks = root.putArray("chunks");
        chunk(chunks.addObject(), 0, false, 100, 245, CHUNK_0_DIGEST);
        chunk(chunks.addObject(), 1, true, 100, 245, CHUNK_1_DIGEST);
        return root;
    }

    private static void shared(ObjectNode root, String schema, long sequence, String signerVersion) {
        root.put("manifest_schema", schema);
        root.put("migration_set_id", SUBJECT.migrationSetId());
        root.put("environment", SUBJECT.environment());
        root.put("database_instance_fingerprint", SUBJECT.databaseInstanceFingerprint());
        root.put("schema", SUBJECT.schema());
        root.put("flyway_set_digest", SUBJECT.flywaySetDigest());
        root.put("global_sequence", sequence);
        root.put("signer_key_version", signerVersion);
    }

    private static void chunk(
            ObjectNode chunk,
            int index,
            boolean terminal,
            long plaintext,
            long envelope,
            String digest) {
        chunk.put("index", index);
        chunk.put("final", terminal);
        chunk.put("plaintext_size", plaintext);
        chunk.put("envelope_size", envelope);
        chunk.put("sha256_digest", digest);
    }

    private SignedMigrationManifestVerifier verifier(
            MigrationPreflightProperties properties,
            PairAdmissionStore store) {
        return new SignedMigrationManifestVerifier(properties, store, CLOCK);
    }

    private MigrationPreflightProperties activeOnly(KeyPair keyPair, String version) {
        return properties(List.of(anchor(keyPair, version, AnchorState.ACTIVE, null)));
    }

    private MigrationPreflightProperties properties(List<SignerAnchor> anchors) {
        return new MigrationPreflightProperties(
                anchors, Set.of(WRITER), Set.of("snapshot-recovery.v1"));
    }

    private static SignerAnchor anchor(
            KeyPair pair,
            String version,
            AnchorState state,
            Long maxSequence) {
        byte[] encoded = pair.getPublic().getEncoded();
        return new SignerAnchor(
                version, state, hex(sha256(encoded)), Base64.getEncoder().encodeToString(encoded), maxSequence);
    }

    private static byte[] sign(KeyPair pair, byte[] message) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(message);
        return signature.sign();
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static String text(JsonNode root, String field) {
        return root.required(field).asText();
    }

    private static void assertClosed(JsonNode schema) {
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required").size()).isEqualTo(schema.path("properties").size());
    }

    private static void assertRejected(
            SignedMigrationManifestVerifier verifier,
            PairedAdmissionRequest request,
            FailureCode code) {
        assertThatThrownBy(() -> verifier.verifyAndAdmit(request))
                .isInstanceOfSatisfying(VerificationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code))
                .hasMessage("migration manifest pair rejected");
    }

    private static boolean admitted(
            SignedMigrationManifestVerifier verifier,
            PairedAdmissionRequest request) {
        try {
            verifier.verifyAndAdmit(request);
            return true;
        } catch (VerificationException exception) {
            return false;
        }
    }

    private record PairFiles(
            Path writerManifest,
            Path writerSignature,
            Path snapshotManifest,
            Path snapshotSignature) {
        PairedAdmissionRequest request() {
            return new PairedAdmissionRequest(
                    writerManifest, writerSignature, snapshotManifest, snapshotSignature, SUBJECT);
        }
    }

    private record NamedMutation(
            String name,
            FailureCode expectedCode,
            Consumer<ObjectNode> mutation) {

        private NamedMutation(String name, Consumer<ObjectNode> mutation) {
            this(name, FailureCode.SNAPSHOT_INVENTORY_INVALID, mutation);
        }
    }

    private static final class BarrierStore implements PairAdmissionStore {
        private final InMemoryPairAdmissionStore delegate;
        private final CountDownLatch reads = new CountDownLatch(2);

        private BarrierStore(InMemoryPairAdmissionStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PairTuple> current() {
            Optional<PairTuple> current = delegate.current();
            reads.countDown();
            try {
                reads.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return current;
        }

        @Override
        public SignedMigrationManifestVerifier.AdmissionDecision compareAndSet(
                Optional<PairTuple> expected,
                PairTuple candidate,
                SignerAnchor signer) {
            return delegate.compareAndSet(expected, candidate, signer);
        }
    }
}
