package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Sole policy owner for activation, readability and reference-gated retirement. */
public final class KeyLifecycleService {

    public static final String SANITIZED_FAILURE = "key lifecycle operation failed";

    public enum WrapDisposition {
        READY,
        PREPARE_AND_ACTIVATE,
        BLOCKED
    }

    public record Activation(KeyReferenceRepository.KeyReference previous,
                             KeyReferenceRepository.KeyReference active) {
        public Activation {
            Objects.requireNonNull(active, "active");
        }
    }

    public record RetirementProof(long liveReferences, byte[] inventoryDigest) {
        public RetirementProof {
            if (liveReferences < 0 || inventoryDigest == null || inventoryDigest.length != 32) {
                throw new IllegalArgumentException("invalid retirement proof");
            }
            inventoryDigest = inventoryDigest.clone();
        }

        @Override
        public byte[] inventoryDigest() {
            return inventoryDigest.clone();
        }

        @Override
        public String toString() {
            return "RetirementProof[liveReferences=" + liveReferences + ", digest=[redacted]]";
        }
    }

    private final KeyReferenceRepository repository;
    private final EnvelopeReferenceInventory inventory;

    public KeyLifecycleService(KeyReferenceRepository repository,
                               EnvelopeReferenceInventory inventory) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    /** Activates one fully prepared reference and moves the old owner to its read-only state. */
    public Activation activate(KeyReferenceRepository.Purpose purpose, long preparedVersion) {
        try {
            Objects.requireNonNull(purpose, "purpose");
            List<KeyReferenceRepository.KeyReference> keys = repository.findByPurpose(purpose);
            assertPurposeInvariant(purpose, keys);
            assertTokenPurposeIsolation(repository.findAll());

            KeyReferenceRepository.KeyReference prepared = keys.stream()
                    .filter(key -> key.keyVersion() == preparedVersion).findFirst()
                    .orElseThrow(KeyLifecycleService::failure);
            if (prepared.state() != KeyState.PREPARED) {
                throw failure();
            }
            KeyReferenceRepository.KeyReference old = keys.stream()
                    .filter(key -> key.state().ownsActiveSlot()).findFirst().orElse(null);
            List<KeyReferenceRepository.Transition> transitions = new ArrayList<>();
            if (old != null) {
                transitions.add(new KeyReferenceRepository.Transition(old.keyVersion(), old.state(),
                        old.optimisticVersion(), previousState(purpose)));
            }
            transitions.add(new KeyReferenceRepository.Transition(prepared.keyVersion(), KeyState.PREPARED,
                    prepared.optimisticVersion(), KeyState.ACTIVE));
            if (!repository.transitionAtomically(purpose, transitions)) {
                throw failure();
            }
            KeyReferenceRepository.KeyReference active = refreshed(purpose, preparedVersion,
                    KeyState.ACTIVE);
            KeyReferenceRepository.KeyReference previous = old == null ? null
                    : refreshed(purpose, old.keyVersion(), previousState(purpose));
            assertPurposeInvariant(purpose, repository.findByPurpose(purpose));
            return new Activation(previous, active);
        } catch (RuntimeException failure) {
            throw sanitized(failure);
        }
    }

    /** Reflects the independently persisted reservation counter; it never mutates or refunds it. */
    public WrapDisposition wrapDisposition(long keyVersion) {
        KeyReferenceRepository.KeyReference key = key(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, keyVersion);
        if (!key.state().permitsWrap()) {
            return WrapDisposition.BLOCKED;
        }
        if (key.wrapOperationCount() >= KekWrapUsageRepository.HARD_CEILING) {
            return WrapDisposition.BLOCKED;
        }
        if (key.wrapOperationCount() >= KekWrapUsageRepository.ROTATION_REQUIRED_AT) {
            if (!key.rotationRequired() || key.state() != KeyState.ROTATION_REQUIRED) {
                throw failure();
            }
            return WrapDisposition.PREPARE_AND_ACTIVATE;
        }
        if (key.rotationRequired() || key.state() == KeyState.ROTATION_REQUIRED) {
            throw failure();
        }
        return WrapDisposition.READY;
    }

    public KeyReferenceRepository.KeyReference uniqueActive(KeyReferenceRepository.Purpose purpose) {
        KeyReferenceRepository.KeyReference active = repository.uniqueActive(purpose)
                .orElseThrow(KeyLifecycleService::failure);
        assertPurposeInvariant(purpose, repository.findByPurpose(purpose));
        return active;
    }

    public void assertReadable(KeyReferenceRepository.Purpose purpose, long keyVersion) {
        if (!key(purpose, keyVersion).state().permitsUnwrap()) {
            throw failure();
        }
    }

    public void assertDigestVerifiable(KeyReferenceRepository.Purpose purpose, long keyVersion) {
        if (!purpose.isTokenDigest() || !key(purpose, keyVersion).state().permitsDigestVerification()) {
            throw failure();
        }
    }

    /** A retired metadata row remains for audit and provider cleanup is intentionally absent. */
    public RetirementProof retire(KeyReferenceRepository.Purpose purpose, long keyVersion) {
        try {
            KeyReferenceRepository.KeyReference current = key(purpose, keyVersion);
            KeyState expected = previousState(purpose);
            if (current.state() != expected || repository.uniqueActive(purpose).isEmpty()) {
                throw failure();
            }
            EnvelopeReferenceInventory.Snapshot snapshot = inventory.snapshot();
            long live = snapshot.count(purpose, keyVersion);
            RetirementProof proof = new RetirementProof(live, snapshot.digest());
            if (live != 0 || !repository.transitionAtomically(purpose, List.of(
                    new KeyReferenceRepository.Transition(keyVersion, expected,
                            current.optimisticVersion(), KeyState.RETIRED)))) {
                throw failure();
            }
            refreshed(purpose, keyVersion, KeyState.RETIRED);
            return proof;
        } catch (RuntimeException failure) {
            throw sanitized(failure);
        }
    }

    private KeyReferenceRepository.KeyReference key(KeyReferenceRepository.Purpose purpose,
                                                     long keyVersion) {
        if (purpose == null || keyVersion < 1) {
            throw failure();
        }
        return repository.findByPurpose(purpose).stream()
                .filter(key -> key.keyVersion() == keyVersion).findFirst()
                .orElseThrow(KeyLifecycleService::failure);
    }

    private KeyReferenceRepository.KeyReference refreshed(KeyReferenceRepository.Purpose purpose,
                                                           long keyVersion,
                                                           KeyState expected) {
        KeyReferenceRepository.KeyReference key = key(purpose, keyVersion);
        if (key.state() != expected) {
            throw failure();
        }
        return key;
    }

    private static KeyState previousState(KeyReferenceRepository.Purpose purpose) {
        return purpose.usesEncryptionLifecycle() ? KeyState.DECRYPT_ONLY : KeyState.RETIRING;
    }

    private static void assertPurposeInvariant(KeyReferenceRepository.Purpose purpose,
                                               List<KeyReferenceRepository.KeyReference> keys) {
        if (keys == null || keys.isEmpty() || keys.stream().anyMatch(key -> key.purpose() != purpose)
                || keys.stream().filter(key -> key.state().ownsActiveSlot()).count() > 1) {
            throw failure();
        }
    }

    private static void assertTokenPurposeIsolation(
            List<KeyReferenceRepository.KeyReference> references) {
        List<KeyReferenceRepository.KeyReference> tokens = references.stream()
                .filter(key -> key.purpose().isTokenDigest()).toList();
        for (int left = 0; left < tokens.size(); left++) {
            for (int right = left + 1; right < tokens.size(); right++) {
                KeyReferenceRepository.KeyReference first = tokens.get(left);
                KeyReferenceRepository.KeyReference second = tokens.get(right);
                if (first.purpose() != second.purpose()
                        && first.providerId().equals(second.providerId())
                        && first.providerKeyReference().equalsIgnoreCase(second.providerKeyReference())) {
                    throw failure();
                }
            }
        }
    }

    private static IllegalStateException failure() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }

    private static IllegalStateException sanitized(RuntimeException failure) {
        return failure instanceof IllegalStateException stateFailure
                && SANITIZED_FAILURE.equals(stateFailure.getMessage()) ? stateFailure : failure();
    }
}
