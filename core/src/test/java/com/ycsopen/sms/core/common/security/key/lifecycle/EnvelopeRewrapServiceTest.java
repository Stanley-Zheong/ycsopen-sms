package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeRewrapServiceTest {

    private static final String OLD_KEY = "field-kek-v1";
    private static final String NEW_KEY = "field-kek-v2";
    private static final EnvelopeCodec ENVELOPES = new EnvelopeCodec();

    @Test
    void rewrapChangesOnlyWrapMetadataVerifiesAndAdvancesCheckpointAtomically() {
        Fixture fixture = fixture();
        CipherEnvelope before = ENVELOPES.decode(fixture.store.envelope,
                EnvelopeCodec.Target.DATABASE_FIELD);

        EnvelopeRewrapService.BatchResult result = fixture.service.rewrap(OLD_KEY, 10);

        CipherEnvelope after = ENVELOPES.decode(fixture.store.envelope,
                EnvelopeCodec.Target.DATABASE_FIELD);
        assertThat(result.applied()).isOne();
        assertThat(result.finalCheckpoint()).isEqualTo(1);
        assertThat(after.keyReference()).isEqualTo(NEW_KEY);
        assertThat(after.wrapNonce()).isNotEqualTo(before.wrapNonce());
        assertThat(after.wrappedDek()).isNotEqualTo(before.wrappedDek());
        assertThat(after.dataNonce()).containsExactly(before.dataNonce());
        assertThat(after.ciphertext()).containsExactly(before.ciphertext());
        assertThat(new ProtectedFieldCodec(ENVELOPES, fixture.keys, new SecureRandom(), NEW_KEY)
                .unprotect(fixture.store.envelope, fixture.context,
                        EnvelopeCodec.Target.DATABASE_FIELD))
                .containsExactly(fixture.plaintext);
    }

    @Test
    void failedCommitConsumesWrapButRestartSafelyRetriesOriginalDigest() {
        Fixture fixture = fixture();
        fixture.store.driftNextCommit = true;

        assertThatThrownBy(() -> fixture.service.rewrap(OLD_KEY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EnvelopeRewrapService.SANITIZED_FAILURE);
        assertThat(fixture.store.checkpoint).isZero();
        assertThat(ENVELOPES.decode(fixture.store.envelope,
                EnvelopeCodec.Target.DATABASE_FIELD).keyReference()).isEqualTo(OLD_KEY);
        int afterFailure = fixture.keys.wrapCalls.get();

        EnvelopeRewrapService.BatchResult resumed = fixture.service.rewrap(OLD_KEY, 1);
        assertThat(resumed.applied()).isOne();
        assertThat(fixture.store.checkpoint).isEqualTo(1);
        assertThat(fixture.keys.wrapCalls.get()).isEqualTo(afterFailure + 1);
    }

    @Test
    void contextDriftFailsBeforeAnyCasCommit() {
        Fixture fixture = fixture();
        fixture.store.context = context("different-resource");

        assertThatThrownBy(() -> fixture.service.rewrap(OLD_KEY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EnvelopeRewrapService.SANITIZED_FAILURE);
        assertThat(fixture.store.commitCalls).isZero();
        assertThat(fixture.store.checkpoint).isZero();
    }

    @Test
    void missingOrNonActiveTargetKeyFailsClosedWithoutReadingCandidates() {
        Fixture fixture = fixture();
        FixedKeys noActive = new FixedKeys(List.of(key(1, OLD_KEY, KeyState.DECRYPT_ONLY)));
        EnvelopeRewrapService service = new EnvelopeRewrapService(noActive, ENVELOPES,
                fixture.keys, fixture.store);

        assertThatThrownBy(() -> service.rewrap(OLD_KEY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EnvelopeRewrapService.SANITIZED_FAILURE);
        assertThat(fixture.store.nextCalls).isZero();
    }

    @Test
    void staleAdapterFailsClosedAfterDatabaseActivationAndRebuiltAdapterCompletesRewrap() {
        FakeKeyPort stale = FakeKeyPort.beforeActivation();
        byte[] plaintext = "adapter-reload-boundary".getBytes(StandardCharsets.UTF_8);
        ProtectionContext context = context("message-reload");
        byte[] encoded = new ProtectedFieldCodec(ENVELOPES, stale, new SecureRandom(), OLD_KEY)
                .protect(plaintext, context, EnvelopeCodec.Target.DATABASE_FIELD);
        InMemoryStore store = new InMemoryStore(encoded, context);
        FixedKeys activatedDatabase = new FixedKeys(List.of(
                key(1, OLD_KEY, KeyState.DECRYPT_ONLY), key(2, NEW_KEY, KeyState.ACTIVE)));
        int wrapsBeforeRewrap = stale.wrapCalls.get();

        assertThatThrownBy(() -> new EnvelopeRewrapService(activatedDatabase, ENVELOPES,
                stale, store).rewrap(OLD_KEY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EnvelopeRewrapService.SANITIZED_FAILURE);
        assertThat(stale.wrapCalls.get()).isEqualTo(wrapsBeforeRewrap);
        assertThat(store.commitCalls).isZero();
        assertThat(store.checkpoint).isZero();
        assertThat(ENVELOPES.decode(store.envelope,
                EnvelopeCodec.Target.DATABASE_FIELD).keyReference()).isEqualTo(OLD_KEY);

        FakeKeyPort rebuilt = FakeKeyPort.afterActivation(stale.nonceSequence);
        EnvelopeRewrapService.BatchResult result = new EnvelopeRewrapService(activatedDatabase,
                ENVELOPES, rebuilt, store).rewrap(OLD_KEY, 1);
        assertThat(result.applied()).isOne();
        assertThat(store.commitCalls).isOne();
        assertThat(ENVELOPES.decode(store.envelope,
                EnvelopeCodec.Target.DATABASE_FIELD).keyReference()).isEqualTo(NEW_KEY);
        assertThat(new ProtectedFieldCodec(ENVELOPES, rebuilt, new SecureRandom(), NEW_KEY)
                .unprotect(store.envelope, context, EnvelopeCodec.Target.DATABASE_FIELD))
                .containsExactly(plaintext);
    }

    private static Fixture fixture() {
        FakeKeyPort oldPort = FakeKeyPort.beforeActivation();
        byte[] plaintext = "phase-03-rewrap".getBytes(StandardCharsets.UTF_8);
        ProtectionContext context = context("message-20");
        byte[] encoded = new ProtectedFieldCodec(ENVELOPES, oldPort, new SecureRandom(), OLD_KEY)
                .protect(plaintext, context, EnvelopeCodec.Target.DATABASE_FIELD);
        FakeKeyPort rebuilt = FakeKeyPort.afterActivation(oldPort.nonceSequence);
        InMemoryStore store = new InMemoryStore(encoded, context);
        FixedKeys references = new FixedKeys(List.of(
                key(1, OLD_KEY, KeyState.DECRYPT_ONLY), key(2, NEW_KEY, KeyState.ACTIVE)));
        return new Fixture(new EnvelopeRewrapService(references, ENVELOPES, rebuilt, store),
                rebuilt, store, context, plaintext);
    }

    private static KeyReferenceRepository.KeyReference key(long version,
                                                            String reference,
                                                            KeyState state) {
        return new KeyReferenceRepository.KeyReference(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, version, "pkcs11",
                reference, state, 0, false, 0);
    }

    private static ProtectionContext context(String resource) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "message_tasks", "MessageTask", "mobile", "tenant:7", resource);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Fixture(EnvelopeRewrapService service,
                           FakeKeyPort keys,
                           InMemoryStore store,
                           ProtectionContext context,
                           byte[] plaintext) {
    }

    private static final class FixedKeys implements KeyReferenceRepository {
        private final List<KeyReference> values;

        private FixedKeys(List<KeyReference> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public List<KeyReference> findByPurpose(Purpose purpose) {
            return values.stream().filter(key -> key.purpose() == purpose).toList();
        }

        @Override
        public List<KeyReference> findAll() {
            return values;
        }

        @Override
        public boolean transitionAtomically(Purpose purpose, List<Transition> transitions) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryStore implements EnvelopeRewrapService.Store {
        private byte[] envelope;
        private final byte[] originalDigest;
        private ProtectionContext context;
        private long checkpoint;
        private boolean driftNextCommit;
        private int commitCalls;
        private int nextCalls;

        private InMemoryStore(byte[] envelope, ProtectionContext context) {
            this.envelope = envelope.clone();
            this.originalDigest = sha256(envelope);
            this.context = context;
        }

        @Override
        public long checkpoint(String oldKeyReference, String newKeyReference) {
            return checkpoint;
        }

        @Override
        public Optional<EnvelopeRewrapService.Candidate> next(String oldKeyReference,
                                                               long afterSequence) {
            nextCalls++;
            if (afterSequence >= 1 || !OLD_KEY.equals(ENVELOPES.decode(envelope,
                    EnvelopeCodec.Target.DATABASE_FIELD).keyReference())) {
                return Optional.empty();
            }
            return Optional.of(new EnvelopeRewrapService.Candidate(1, sha256(new byte[]{1}),
                    originalDigest, envelope, context, EnvelopeCodec.Target.DATABASE_FIELD));
        }

        @Override
        public EnvelopeRewrapService.CommitOutcome replaceByOriginalDigestAndCheckpoint(
                String oldKeyReference,
                String newKeyReference,
                EnvelopeRewrapService.Candidate candidate,
                byte[] rewrittenEnvelope,
                byte[] rewrittenEnvelopeDigest) {
            commitCalls++;
            if (driftNextCommit) {
                driftNextCommit = false;
                return EnvelopeRewrapService.CommitOutcome.DRIFT;
            }
            if (checkpoint != candidate.sequence() - 1
                    || !MessageDigest.isEqual(sha256(envelope), candidate.originalEnvelopeDigest())
                    || !MessageDigest.isEqual(sha256(rewrittenEnvelope), rewrittenEnvelopeDigest)) {
                return EnvelopeRewrapService.CommitOutcome.DRIFT;
            }
            envelope = rewrittenEnvelope.clone();
            checkpoint = candidate.sequence();
            return EnvelopeRewrapService.CommitOutcome.APPLIED;
        }
    }

    private static final class FakeKeyPort implements KeyProtectionPort {
        private final Map<String, byte[]> keyBytes = Map.of(
                OLD_KEY, repeated((byte) 0x11), NEW_KEY, repeated((byte) 0x22));
        private final AtomicInteger nonceSequence;
        private final AtomicInteger wrapCalls = new AtomicInteger();
        private final String activeReference;
        private final Map<String, KeyState> states;

        private FakeKeyPort(String activeReference, Map<String, KeyState> states,
                            AtomicInteger nonceSequence) {
            this.activeReference = activeReference;
            this.states = Map.copyOf(states);
            this.nonceSequence = nonceSequence;
        }

        static FakeKeyPort beforeActivation() {
            return new FakeKeyPort(OLD_KEY, Map.of(
                    OLD_KEY, KeyState.ACTIVE, NEW_KEY, KeyState.PREPARED),
                    new AtomicInteger());
        }

        static FakeKeyPort afterActivation(AtomicInteger nonceSequence) {
            return new FakeKeyPort(NEW_KEY, Map.of(
                    OLD_KEY, KeyState.DECRYPT_ONLY, NEW_KEY, KeyState.ACTIVE), nonceSequence);
        }

        @Override
        public WrappedDataKey wrap(byte[] dataEncryptionKey,
                                   byte[] authenticatedHeader,
                                   ProtectionContext semanticContext) {
            if (states.get(activeReference) != KeyState.ACTIVE) {
                throw new IllegalStateException("key policy");
            }
            requireHeaderReference(authenticatedHeader, activeReference);
            wrapCalls.incrementAndGet();
            byte[] nonce = new byte[WrappedDataKey.WRAP_NONCE_BYTES];
            ByteBuffer.wrap(nonce).putInt(8, nonceSequence.incrementAndGet());
            return new WrappedDataKey(activeReference, nonce,
                    crypt(Cipher.ENCRYPT_MODE, keyBytes.get(activeReference), nonce,
                            aad(authenticatedHeader, semanticContext), dataEncryptionKey));
        }

        @Override
        public byte[] unwrap(WrappedDataKey wrappedDataKey,
                             byte[] authenticatedHeader,
                             ProtectionContext semanticContext) {
            byte[] key = keyBytes.get(wrappedDataKey.keyReference());
            KeyState state = states.get(wrappedDataKey.keyReference());
            if (key == null || state == null || !state.permitsUnwrap()) {
                throw new IllegalStateException("unavailable");
            }
            requireHeaderReference(authenticatedHeader, wrappedDataKey.keyReference());
            return crypt(Cipher.DECRYPT_MODE, key, wrappedDataKey.wrapNonce(),
                    aad(authenticatedHeader, semanticContext), wrappedDataKey.wrappedDek());
        }

        @Override
        public KeyHealth health() {
            return new KeyHealth(KeyHealth.Status.READY);
        }

        private static byte[] aad(byte[] header, ProtectionContext context) {
            byte[] semantic = context.canonicalBytes();
            return ByteBuffer.allocate(8 + header.length + semantic.length)
                    .putInt(header.length).put(header).putInt(semantic.length).put(semantic).array();
        }

        private static void requireHeaderReference(byte[] header, String expectedReference) {
            if (header == null || header.length < EnvelopeCodec.FIXED_HEADER_BYTES) {
                throw new IllegalStateException("invalid header");
            }
            int providerLength = Byte.toUnsignedInt(header[9]);
            int referenceLength = Byte.toUnsignedInt(header[10]);
            int referenceOffset = EnvelopeCodec.FIXED_HEADER_BYTES + providerLength;
            if (referenceLength < 1 || referenceOffset + referenceLength != header.length) {
                throw new IllegalStateException("invalid header");
            }
            String actual = new String(header, referenceOffset, referenceLength,
                    StandardCharsets.US_ASCII);
            if (!expectedReference.equals(actual)) {
                throw new IllegalStateException("key policy");
            }
        }

        private static byte[] crypt(int mode,
                                    byte[] key,
                                    byte[] nonce,
                                    byte[] aad,
                                    byte[] value) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                cipher.updateAAD(aad);
                return cipher.doFinal(value);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("crypto failure", exception);
            }
        }

        private static byte[] repeated(byte value) {
            byte[] result = new byte[32];
            Arrays.fill(result, value);
            return result;
        }
    }
}
