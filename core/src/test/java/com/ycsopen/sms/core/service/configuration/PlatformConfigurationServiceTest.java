package com.ycsopen.sms.core.service.configuration;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformConfigurationServiceTest {

    @Test
    void delayedOlderApplyCannotReplaceANewerRuntimeSnapshot() throws Exception {
        PlatformConfigurationRegistry registry = new PlatformConfigurationRegistry();
        PlatformConfigurationRuntime runtime = new PlatformConfigurationRuntime(registry);
        PlatformConfigurationRuntime.Prepared older = runtime.prepare(1,
                registry.mergeAndValidate(registry.defaults(), Map.of(
                        PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8")));
        PlatformConfigurationRuntime.Prepared newer = runtime.prepare(2,
                registry.mergeAndValidate(registry.defaults(), Map.of(
                        PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "10")));
        CountDownLatch olderReady = new CountDownLatch(1);
        CountDownLatch releaseOlder = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var delayedApply = executor.submit(() -> {
                olderReady.countDown();
                if (!releaseOlder.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("older apply was not released");
                }
                runtime.apply(older);
                return null;
            });
            assertThat(olderReady.await(5, TimeUnit.SECONDS)).isTrue();
            runtime.apply(newer);
            releaseOlder.countDown();
            delayedApply.get(5, TimeUnit.SECONDS);
        }

        assertThat(runtime.version()).isEqualTo(2);
        assertThat(runtime.loginMaxFailures()).isEqualTo(10);
    }

    @Test
    void appliesRuntimeOnlyAfterTheDatabaseActivationReturns() {
        PlatformConfigurationRegistry registry = new PlatformConfigurationRegistry();
        List<String> events = new ArrayList<>();
        FakeStore store = new FakeStore(registry, events);
        PlatformConfigurationRuntime runtime = new PlatformConfigurationRuntime(registry,
                prepared -> events.add("prepare:" + prepared.version()));
        PlatformConfigurationService service = new PlatformConfigurationService(store, registry, runtime);
        long draft = service.stage(new PlatformConfigurationService.StageCommand(
                0, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8"),
                "raise threshold", 7));

        PlatformConfigurationService.Activation result = service.activate(
                new PlatformConfigurationService.ActivateCommand(draft, 0, "apply", 7));

        assertThat(result.activeVersion()).isEqualTo(draft);
        assertThat(runtime.loginMaxFailures()).isEqualTo(8);
        assertThat(events).containsSubsequence("prepare:" + draft, "commit:" + draft, "applied:" + draft);
    }

    @Test
    void prepareRejectionLeavesDatabaseAndRuntimeOnThePriorVersion() {
        PlatformConfigurationRegistry registry = new PlatformConfigurationRegistry();
        FakeStore store = new FakeStore(registry, new ArrayList<>());
        PlatformConfigurationRuntime runtime = new PlatformConfigurationRuntime(registry, prepared -> {
            if (prepared.values().get(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES).equals("9")) {
                throw new PlatformConfigurationRuntime.ReloadRejected("TEST_REJECTED");
            }
        });
        PlatformConfigurationService service = new PlatformConfigurationService(store, registry, runtime);
        long draft = service.stage(new PlatformConfigurationService.StageCommand(
                0, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "9"), "unsafe", 7));

        assertThatThrownBy(() -> service.activate(
                new PlatformConfigurationService.ActivateCommand(draft, 0, "apply", 7)))
                .isInstanceOf(PlatformConfigurationService.Failure.class)
                .extracting(error -> ((PlatformConfigurationService.Failure) error).code())
                .isEqualTo(PlatformConfigurationService.FailureCode.RELOAD_REJECTED);
        assertThat(store.activeVersion).isZero();
        assertThat(store.versions.get(draft).status())
                .isEqualTo(PlatformConfigurationService.VersionStatus.RELOAD_REJECTED);
        assertThat(runtime.version()).isZero();
        assertThat(runtime.loginMaxFailures()).isEqualTo(5);
    }

    @Test
    void staleActivationCannotReachRuntimeApply() {
        PlatformConfigurationRegistry registry = new PlatformConfigurationRegistry();
        FakeStore store = new FakeStore(registry, new ArrayList<>());
        PlatformConfigurationRuntime runtime = new PlatformConfigurationRuntime(registry);
        PlatformConfigurationService service = new PlatformConfigurationService(store, registry, runtime);
        long draft = service.stage(new PlatformConfigurationService.StageCommand(
                0, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8"), "draft", 7));
        store.activeVersion = 99;

        assertThatThrownBy(() -> service.activate(
                new PlatformConfigurationService.ActivateCommand(draft, 0, "stale", 7)))
                .isInstanceOf(PlatformConfigurationService.Failure.class)
                .extracting(error -> ((PlatformConfigurationService.Failure) error).code())
                .isEqualTo(PlatformConfigurationService.FailureCode.STALE_VERSION);
        assertThat(runtime.version()).isZero();
    }

    @Test
    void rollbackCopiesHistoryIntoANewVersion() {
        PlatformConfigurationRegistry registry = new PlatformConfigurationRegistry();
        FakeStore store = new FakeStore(registry, new ArrayList<>());
        PlatformConfigurationRuntime runtime = new PlatformConfigurationRuntime(registry);
        PlatformConfigurationService service = new PlatformConfigurationService(store, registry, runtime);
        long firstDraft = service.stage(new PlatformConfigurationService.StageCommand(
                0, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8"), "first", 7));
        service.activate(new PlatformConfigurationService.ActivateCommand(firstDraft, 0, "first active", 7));
        long secondDraft = service.stage(new PlatformConfigurationService.StageCommand(
                firstDraft, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "10"), "second", 7));
        service.activate(new PlatformConfigurationService.ActivateCommand(
                secondDraft, firstDraft, "second active", 7));

        PlatformConfigurationService.Activation rollback = service.rollback(
                new PlatformConfigurationService.RollbackCommand(
                        firstDraft, secondDraft, "restore first", 7));

        assertThat(rollback.activeVersion()).isGreaterThan(secondDraft);
        assertThat(runtime.loginMaxFailures()).isEqualTo(8);
        assertThat(store.versions.get(firstDraft).status())
                .isEqualTo(PlatformConfigurationService.VersionStatus.SUPERSEDED);
        assertThat(store.versions.get(rollback.activeVersion()).sourceVersionId()).isEqualTo(firstDraft);
    }

    private static final class FakeStore implements PlatformConfigurationService.Store {
        private final PlatformConfigurationRegistry registry;
        private final List<String> events;
        private final Map<Long, PlatformConfigurationService.StoredVersion> versions = new LinkedHashMap<>();
        private long sequence;
        private long activeVersion;

        private FakeStore(PlatformConfigurationRegistry registry, List<String> events) {
            this.registry = registry;
            this.events = events;
        }

        @Override
        public PlatformConfigurationService.Current current() {
            Map<String, String> values = activeVersion == 0
                    ? registry.defaults() : versions.get(activeVersion).values();
            return new PlatformConfigurationService.Current(activeVersion, sequence, values,
                    activeVersion == 0 ? registry.checksum(values) : versions.get(activeVersion).checksum(),
                    activeVersion == 0 ? null : 7L, activeVersion == 0 ? null : "admin",
                    activeVersion == 0 ? null : LocalDateTime.now(), "APPLIED", null);
        }

        @Override
        public long insertDraft(long expectedActiveVersion, Map<String, String> values,
                                List<String> changedKeys, String checksum, String reason,
                                long actorUserId, Long sourceVersionId) {
            if (activeVersion != expectedActiveVersion) {
                throw new PlatformConfigurationService.Failure(
                        PlatformConfigurationService.FailureCode.STALE_VERSION);
            }
            versions.replaceAll((id, version) -> version.status() == PlatformConfigurationService.VersionStatus.DRAFT
                    ? version.withStatus(PlatformConfigurationService.VersionStatus.ABANDONED, null) : version);
            long id = ++sequence;
            versions.put(id, new PlatformConfigurationService.StoredVersion(id, expectedActiveVersion,
                    sourceVersionId, PlatformConfigurationService.VersionStatus.DRAFT,
                    Map.copyOf(values), List.copyOf(changedKeys), checksum, reason, actorUserId,
                    "admin", LocalDateTime.now(), null, null, null, null));
            return id;
        }

        @Override
        public PlatformConfigurationService.StoredVersion version(long versionId) {
            PlatformConfigurationService.StoredVersion version = versions.get(versionId);
            if (version == null) {
                throw new PlatformConfigurationService.Failure(
                        PlatformConfigurationService.FailureCode.VERSION_NOT_FOUND);
            }
            return version;
        }

        @Override
        public void commitActivation(long versionId, long expectedActiveVersion,
                                     long actorUserId, String reason) {
            if (activeVersion != expectedActiveVersion) {
                throw new PlatformConfigurationService.Failure(
                        PlatformConfigurationService.FailureCode.STALE_VERSION);
            }
            PlatformConfigurationService.StoredVersion target = version(versionId);
            if (target.status() != PlatformConfigurationService.VersionStatus.DRAFT
                    || target.baseVersionId() != expectedActiveVersion) {
                throw new PlatformConfigurationService.Failure(
                        PlatformConfigurationService.FailureCode.VERSION_STATE_INVALID);
            }
            if (activeVersion != 0) {
                versions.put(activeVersion, versions.get(activeVersion)
                        .withStatus(PlatformConfigurationService.VersionStatus.SUPERSEDED, null));
            }
            versions.put(versionId, target.activated(actorUserId, reason));
            activeVersion = versionId;
            events.add("commit:" + versionId);
        }

        @Override
        public void markReloadRejected(long versionId, String safeCode) {
            versions.put(versionId, version(versionId).withStatus(
                    PlatformConfigurationService.VersionStatus.RELOAD_REJECTED, safeCode));
        }

        @Override
        public void markApplied(long versionId) {
            versions.put(versionId, version(versionId).withReloadStatus("APPLIED", null));
            events.add("applied:" + versionId);
        }

        @Override
        public Optional<PlatformConfigurationService.StoredVersion> draft() {
            return versions.values().stream()
                    .filter(version -> version.status() == PlatformConfigurationService.VersionStatus.DRAFT)
                    .reduce((left, right) -> right);
        }

        @Override
        public List<PlatformConfigurationService.StoredVersion> history() {
            return new ArrayList<>(versions.values()).reversed();
        }
    }
}
