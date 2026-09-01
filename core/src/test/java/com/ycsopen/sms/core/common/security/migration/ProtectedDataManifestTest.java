package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.CheckpointState;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.FailureCode;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.KeyHealth;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.KeyState;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.KeyVersion;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.MutationCounters;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.PreflightException;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.PreflightRequest;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.TargetInspection;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.BlindIndexRule;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.Kind;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.MigrationState;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.DeploymentSubject;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmissionRequest;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedBoundary;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedDataManifestTest {

    private static final Path MANIFEST = Path.of("src/main/resources/security/protected-data-inventory.json");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static byte[] manifestBytes;

    @BeforeAll
    static void readManifest() throws IOException {
        manifestBytes = Files.readAllBytes(MANIFEST);
    }

    @Test
    void loadsOnlyTheReviewedTargetUnionAndCanonicalDigest() {
        ProtectedDataManifest manifest = load(manifestBytes);

        assertThat(manifest.targets()).hasSize(29);
        assertThat(manifest.noIndexTargetIds()).containsExactlyInAnyOrder(
                "bulk_sending_items.mobile_encrypted", "uplink_records.mobile_encrypted");
        assertThat(manifest.blindIndexTargetIds()).contains(
                "mobile_portability.mobile_hash",
                "blacklist_entries.mobile_hash",
                "third_party_risk_check_logs.mobile_hash",
                "message_tasks.mobile_hash",
                "unsubscribe_records.mobile_hash");

        ProtectedDataTarget thirdParty = manifest.requireTarget("third_party_risk_check_logs.mobile_hash");
        assertThat(thirdParty.kind()).isEqualTo(Kind.LEGACY_DIGEST);
        assertThat(thirdParty.migrationState()).isEqualTo(MigrationState.MIGRATABLE_SCHEMA_ONLY);
        assertThat(thirdParty.identityColumn()).isEqualTo("id");
        assertThat(manifest.resolvedForMigration()).isTrue();
        assertThat(manifest.unresolvedReasons()).isEmpty();
    }

    @Test
    void rejectsDigestDriftUnknownTargetsAndReviewedDispositionChanges() throws Exception {
        assertThatThrownBy(() -> ProtectedDataManifest.load(
                new ByteArrayInputStream(manifestBytes), "sha256:" + "0".repeat(64)))
                .isInstanceOf(ProtectedDataManifest.ManifestException.class);

        byte[] missingTarget = mutate(root -> {
            ArrayNode targets = (ArrayNode) root.required("targets");
            for (int index = 0; index < targets.size(); index++) {
                if ("bulk_sending_items.mobile_encrypted".equals(targets.get(index).required("id").asText())) {
                    targets.remove(index);
                    break;
                }
            }
            return root;
        });
        assertThatThrownBy(() -> load(missingTarget))
                .isInstanceOf(ProtectedDataManifest.ManifestException.class);

        byte[] inventedIndex = mutate(root -> {
            findTarget(root, "uplink_records.mobile_encrypted").put("blind_index", "REQUIRED_VERSIONED_HMAC");
            return root;
        });
        assertThatThrownBy(() -> load(inventedIndex))
                .isInstanceOf(ProtectedDataManifest.ManifestException.class);

        byte[] reviewRequired = mutate(root -> {
            ((ObjectNode) root.required("candidates").get(0)).put("classification", "REVIEW_REQUIRED");
            return root;
        });
        assertThat(load(reviewRequired).resolvedForMigration()).isFalse();
    }

    @Test
    void acceptsOnlyAResolvedCapacitySafeKeyedAndPairedPreflight() throws Exception {
        ProtectedDataManifest manifest = resolvedManifest();
        AtomicInteger pairCalls = new AtomicInteger();
        AtomicReference<MutationCounters> mutations = new AtomicReference<>(zeroMutations());
        PairedBoundary boundary = request -> {
            pairCalls.incrementAndGet();
            return acceptedPair(request.expectedSubject());
        };
        MigrationPreflight preflight = new MigrationPreflight(manifest, boundary, mutations::get);

        MigrationPreflight.Admission admission = preflight.preflight(validRequest(manifest));

        assertThat(admission.manifestDigest()).isEqualTo(manifest.digest());
        assertThat(admission.pairDigest()).isEqualTo("c".repeat(64));
        assertThat(pairCalls).hasValue(1);
        assertThat(mutations.get()).isEqualTo(zeroMutations());
    }

    @Test
    void unresolvedReviewedManifestBlocksBeforeThePairedBoundary() throws Exception {
        ProtectedDataManifest manifest = unresolvedManifest();
        AtomicInteger pairCalls = new AtomicInteger();
        AtomicReference<MutationCounters> mutations = new AtomicReference<>(zeroMutations());
        MigrationPreflight preflight = new MigrationPreflight(manifest, request -> {
            pairCalls.incrementAndGet();
            return acceptedPair(request.expectedSubject());
        }, mutations::get);

        assertRejected(preflight, validRequest(manifest), FailureCode.MANIFEST_UNRESOLVED);
        assertThat(pairCalls).hasValue(0);
        assertThat(mutations.get()).isEqualTo(zeroMutations());
    }

    @Test
    void rejectsEveryStaticBoundaryBeforePairAdmissionAndLeavesAllMutationCountsZero() throws Exception {
        ProtectedDataManifest manifest = resolvedManifest();
        AtomicInteger pairCalls = new AtomicInteger();
        AtomicReference<MutationCounters> mutations = new AtomicReference<>(zeroMutations());
        PairedBoundary boundary = request -> {
            pairCalls.incrementAndGet();
            return acceptedPair(request.expectedSubject());
        };
        MigrationPreflight preflight = new MigrationPreflight(manifest, boundary, mutations::get);
        PreflightRequest valid = validRequest(manifest);

        Map<String, TargetInspection> missingTarget = new HashMap<>(valid.targetInspections());
        missingTarget.remove("bulk_sending_items.mobile_encrypted");
        assertRejected(preflight, new PreflightRequest(
                missingTarget, valid.keyHealth(), false, valid.pairedAdmissionRequest()),
                FailureCode.TARGET_SET_INCOMPLETE);

        assertRejected(preflight, replaceInspection(valid, "users.phone_encrypted", inspection -> copy(
                inspection, false, inspection.primaryKeyAvailable(), inspection.requiredSchemaIndexPresent(),
                inspection.equalityIndexRowsPresent(), inspection.rowBindingComplete(), inspection.orphanBindingCount(),
                inspection.previousCheckpointState(), inspection.checkpointState(), inspection.legacyFallbackAllowed(),
                inspection.currentJavaReaderOrWriterPresent(), inspection.deployedWriterKnown(),
                inspection.maximumObservedStoredBytes())), FailureCode.ROW_CONTEXT_INCOMPLETE);

        assertRejected(preflight, replaceInspection(valid, "message_tasks.mobile_hash", inspection -> copy(
                inspection, inspection.contextAvailable(), inspection.primaryKeyAvailable(), false,
                inspection.equalityIndexRowsPresent(), inspection.rowBindingComplete(), 1,
                inspection.previousCheckpointState(), inspection.checkpointState(), inspection.legacyFallbackAllowed(),
                inspection.currentJavaReaderOrWriterPresent(), inspection.deployedWriterKnown(),
                inspection.maximumObservedStoredBytes())), FailureCode.BLIND_INDEX_BINDING_INVALID);

        assertRejected(preflight, replaceInspection(valid, "bulk_sending_items.mobile_encrypted", inspection -> copy(
                inspection, inspection.contextAvailable(), inspection.primaryKeyAvailable(), true, true,
                inspection.rowBindingComplete(), inspection.orphanBindingCount(), inspection.previousCheckpointState(),
                inspection.checkpointState(), false, inspection.currentJavaReaderOrWriterPresent(),
                inspection.deployedWriterKnown(), inspection.maximumObservedStoredBytes())),
                FailureCode.NO_INDEX_TARGET_SET_INVALID);

        assertRejected(preflight, replaceInspection(valid, "third_party_risk_check_logs.mobile_hash", inspection -> copy(
                inspection, inspection.contextAvailable(), inspection.primaryKeyAvailable(), true,
                inspection.equalityIndexRowsPresent(), inspection.rowBindingComplete(), inspection.orphanBindingCount(),
                inspection.previousCheckpointState(), inspection.checkpointState(), inspection.legacyFallbackAllowed(),
                true, false, inspection.maximumObservedStoredBytes())), FailureCode.DEPLOYED_WRITER_UNKNOWN);

        assertRejected(preflight, replaceInspection(valid, "message_tasks.mobile_encrypted", inspection -> copy(
                inspection, inspection.contextAvailable(), inspection.primaryKeyAvailable(),
                inspection.requiredSchemaIndexPresent(), inspection.equalityIndexRowsPresent(),
                inspection.rowBindingComplete(), inspection.orphanBindingCount(), CheckpointState.DISCOVERED,
                CheckpointState.CUTOVER, inspection.legacyFallbackAllowed(),
                inspection.currentJavaReaderOrWriterPresent(), inspection.deployedWriterKnown(),
                inspection.maximumObservedStoredBytes())), FailureCode.CHECKPOINT_TRANSITION_INVALID);

        assertRejected(preflight, replaceInspection(valid, "message_tasks.mobile_hash", inspection -> copy(
                inspection, inspection.contextAvailable(), inspection.primaryKeyAvailable(),
                inspection.requiredSchemaIndexPresent(), inspection.equalityIndexRowsPresent(),
                inspection.rowBindingComplete(), inspection.orphanBindingCount(), CheckpointState.SCRUBBED,
                CheckpointState.COMPLETE, true, inspection.currentJavaReaderOrWriterPresent(),
                inspection.deployedWriterKnown(), inspection.maximumObservedStoredBytes())),
                FailureCode.LEGACY_FALLBACK_AFTER_COMPLETE);

        assertRejected(preflight, replaceInspection(valid, "users.phone_encrypted", inspection -> copy(
                inspection, inspection.contextAvailable(), inspection.primaryKeyAvailable(),
                inspection.requiredSchemaIndexPresent(), inspection.equalityIndexRowsPresent(),
                inspection.rowBindingComplete(), inspection.orphanBindingCount(), inspection.previousCheckpointState(),
                inspection.checkpointState(), inspection.legacyFallbackAllowed(),
                inspection.currentJavaReaderOrWriterPresent(), inspection.deployedWriterKnown(), 157)),
                FailureCode.CAPACITY_EXCEEDED);

        Map<String, List<KeyVersion>> missingActive = new HashMap<>(valid.keyHealth().versionsByPurpose());
        missingActive.put("FIELD_ENCRYPTION_KEK", List.of(new KeyVersion(1, KeyState.RETIRING)));
        assertRejected(preflight, new PreflightRequest(
                valid.targetInspections(), new KeyHealth(true, missingActive), false,
                valid.pairedAdmissionRequest()), FailureCode.ACTIVE_KEY_MISSING);

        assertRejected(preflight, new PreflightRequest(
                valid.targetInspections(), new KeyHealth(false, valid.keyHealth().versionsByPurpose()), false,
                valid.pairedAdmissionRequest()), FailureCode.KEY_PROVIDER_UNAVAILABLE);

        assertRejected(preflight, new PreflightRequest(
                valid.targetInspections(), valid.keyHealth(), true, valid.pairedAdmissionRequest()),
                FailureCode.SCHEMA_DRIFT);

        assertThat(pairCalls).hasValue(0);
        assertThat(mutations.get()).isEqualTo(zeroMutations());
    }

    @Test
    void pairedBoundaryFailureCannotAdmitOneRoleOrTouchMigrationCollaborators() throws Exception {
        ProtectedDataManifest manifest = resolvedManifest();
        AtomicReference<MutationCounters> mutations = new AtomicReference<>(zeroMutations());
        PairedBoundary boundary = request -> {
            throw new IllegalStateException("synthetic pair rejection");
        };
        MigrationPreflight preflight = new MigrationPreflight(manifest, boundary, mutations::get);

        assertRejected(preflight, validRequest(manifest), FailureCode.PAIRED_BOUNDARY_REJECTED);
        assertThat(mutations.get()).isEqualTo(zeroMutations());
    }

    private static ProtectedDataManifest resolvedManifest() throws Exception {
        byte[] resolved = mutate(root -> {
            for (JsonNode surface : root.required("source_surfaces")) {
                ObjectNode row = (ObjectNode) surface;
                row.put("obligation_blocking", false);
                if (row.path("disposition").asText().startsWith("BLOCKING_")) {
                    row.put("disposition", "ADOPTED_TEST_PROTECTED_BOUNDARY");
                }
            }
            ObjectNode readiness = (ObjectNode) root.required("obligation_readiness");
            readiness.put("status", "READY");
            readiness.set("blocking_surface_ids", JSON.createArrayNode());
            readiness.put("reason", "All synthetic preflight dependencies are resolved.");
            return root;
        });
        return load(resolved);
    }

    private static ProtectedDataManifest unresolvedManifest() throws Exception {
        byte[] unresolved = mutate(root -> {
            for (JsonNode surface : root.required("source_surfaces")) {
                ObjectNode row = (ObjectNode) surface;
                if ("tenant-registration-persistence".equals(row.required("id").asText())) {
                    row.put("obligation_blocking", true);
                    row.put("disposition", "BLOCKING_TEST_PROTECTED_BOUNDARY");
                }
            }
            ObjectNode readiness = (ObjectNode) root.required("obligation_readiness");
            readiness.put("status", "BLOCKED_BY_CURRENT_IMPLEMENTATION");
            readiness.set("blocking_surface_ids",
                    JSON.createArrayNode().add("tenant-registration-persistence"));
            readiness.put("reason", "Synthetic current-writer blocker for preflight rejection.");
            return root;
        });
        return load(unresolved);
    }

    private static PreflightRequest validRequest(ProtectedDataManifest manifest) {
        Map<String, TargetInspection> inspections = new HashMap<>();
        manifest.targets().values().forEach(target -> inspections.put(target.id(), new TargetInspection(
                target.id(),
                true,
                target.storageCapacityBytes(),
                target.maximumStoredValueBytes(),
                true,
                true,
                target.requiresBlindIndex(),
                false,
                true,
                0,
                null,
                CheckpointState.DISCOVERED,
                target.blindIndexRule() != BlindIndexRule.EXCLUDED_NO_EQUALITY_CONTRACT,
                false,
                true)));
        KeyHealth keys = new KeyHealth(true, Map.of(
                "FIELD_ENCRYPTION_KEK", List.of(new KeyVersion(1, KeyState.ACTIVE)),
                "MOBILE_BLIND_INDEX", List.of(
                        new KeyVersion(1, KeyState.RETIRING), new KeyVersion(2, KeyState.ACTIVE)),
                "SNAPSHOT_RECOVERY", List.of(new KeyVersion(1, KeyState.ACTIVE))));
        return new PreflightRequest(inspections, keys, false, pairRequest());
    }

    private static PreflightRequest replaceInspection(
            PreflightRequest request,
            String targetId,
            UnaryOperator<TargetInspection> mutation) {
        Map<String, TargetInspection> inspections = new HashMap<>(request.targetInspections());
        inspections.put(targetId, mutation.apply(inspections.get(targetId)));
        return new PreflightRequest(inspections, request.keyHealth(), request.schemaDrift(), request.pairedAdmissionRequest());
    }

    private static TargetInspection copy(
            TargetInspection source,
            boolean context,
            boolean primaryKey,
            boolean requiredIndex,
            boolean equalityRows,
            boolean binding,
            long orphans,
            CheckpointState previous,
            CheckpointState current,
            boolean fallback,
            boolean currentJavaSurface,
            boolean knownWriter,
            long maximumObserved) {
        return new TargetInspection(
                source.targetId(), source.schemaPresent(), source.runtimeStorageCapacityBytes(), maximumObserved,
                context, primaryKey, requiredIndex, equalityRows, binding, orphans, previous, current,
                fallback, currentJavaSurface, knownWriter);
    }

    private static void assertRejected(
            MigrationPreflight preflight,
            PreflightRequest request,
            FailureCode expected) {
        assertThatThrownBy(() -> preflight.preflight(request))
                .isInstanceOfSatisfying(PreflightException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static PairedAdmissionRequest pairRequest() {
        DeploymentSubject subject = new DeploymentSubject(
                "migration-set-v1", "test", "a".repeat(64), "phase03", "b".repeat(64));
        return new PairedAdmissionRequest(
                Path.of("/synthetic/writer.json"),
                Path.of("/synthetic/writer.sig"),
                Path.of("/synthetic/snapshot.json"),
                Path.of("/synthetic/snapshot.sig"),
                subject);
    }

    private static PairedAdmission acceptedPair(DeploymentSubject subject) {
        return new PairedAdmission(
                7,
                "signer-v1",
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                Set.of("ycsopen-core@unit"),
                "snapshot-v1",
                "snapshot-recovery.v1");
    }

    private static MutationCounters zeroMutations() {
        return new MutationCounters(0, 0, 0, 0, 0);
    }

    private static ProtectedDataManifest load(byte[] bytes) {
        return ProtectedDataManifest.load(
                new ByteArrayInputStream(bytes), ProtectedDataManifest.canonicalDigest(bytes));
    }

    private static byte[] mutate(UnaryOperator<ObjectNode> mutation) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(manifestBytes);
        byte[] encoded = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(mutation.apply(root));
        byte[] canonical = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        canonical[canonical.length - 1] = '\n';
        return canonical;
    }

    private static ObjectNode findTarget(ObjectNode root, String id) {
        for (JsonNode target : root.required("targets")) {
            if (id.equals(target.required("id").asText())) {
                return (ObjectNode) target;
            }
        }
        throw new IllegalArgumentException("test target not found");
    }
}
