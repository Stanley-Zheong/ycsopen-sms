package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Restartable DEK-only rewrap with old-digest CAS and full-envelope verification. */
public final class EnvelopeRewrapService {

    public static final String SANITIZED_FAILURE = "envelope rewrap failed";

    public record Candidate(long sequence,
                            byte[] locatorDigest,
                            byte[] originalEnvelopeDigest,
                            byte[] encodedEnvelope,
                            ProtectionContext context,
                            EnvelopeCodec.Target target) {
        public Candidate {
            if (sequence < 1 || locatorDigest == null || locatorDigest.length != 32
                    || originalEnvelopeDigest == null || originalEnvelopeDigest.length != 32
                    || encodedEnvelope == null || context == null || target == null) {
                throw new IllegalArgumentException("invalid rewrap candidate");
            }
            locatorDigest = locatorDigest.clone();
            originalEnvelopeDigest = originalEnvelopeDigest.clone();
            encodedEnvelope = encodedEnvelope.clone();
        }

        @Override
        public byte[] locatorDigest() {
            return locatorDigest.clone();
        }

        @Override
        public byte[] originalEnvelopeDigest() {
            return originalEnvelopeDigest.clone();
        }

        @Override
        public byte[] encodedEnvelope() {
            return encodedEnvelope.clone();
        }

        @Override
        public String toString() {
            return "Candidate[sequence=" + sequence + ", envelope=[redacted]]";
        }
    }

    public enum CommitOutcome {
        APPLIED,
        ALREADY_APPLIED,
        DRIFT
    }

    public interface Store {
        long checkpoint(String oldKeyReference, String newKeyReference);

        Optional<Candidate> next(String oldKeyReference, long afterSequence);

        /**
         * Calls {@code publicationFence} exactly once as the first operation in the durable
         * envelope-CAS/checkpoint transaction, before locking or mutating any business row.
         */
        CommitOutcome replaceByOriginalDigestAndCheckpoint(String oldKeyReference,
                                                           String newKeyReference,
                                                           Candidate candidate,
                                                           byte[] rewrittenEnvelope,
                                                           byte[] rewrittenEnvelopeDigest,
                                                           Runnable publicationFence);
    }

    @FunctionalInterface
    interface EnvelopeVerifier {
        void verify(byte[] encodedEnvelope,
                    ProtectionContext context,
                    EnvelopeCodec.Target target,
                    String expectedKeyReference);
    }

    public record BatchResult(long initialCheckpoint,
                              long finalCheckpoint,
                              int applied,
                              int alreadyApplied,
                              boolean exhausted) {
        public BatchResult {
            if (initialCheckpoint < 0 || finalCheckpoint < initialCheckpoint || applied < 0
                    || alreadyApplied < 0) {
                throw new IllegalArgumentException("invalid rewrap batch result");
            }
        }
    }

    private final KeyReferenceRepository keyReferences;
    private final EnvelopeCodec envelopeCodec;
    private final KeyProtectionPort keyProtection;
    private final Store store;
    private final EnvelopeVerifier verifier;
    private final FieldReferencePublicationFence publicationFence;

    public EnvelopeRewrapService(KeyReferenceRepository keyReferences,
                                 EnvelopeCodec envelopeCodec,
                                 KeyProtectionPort keyProtection,
                                 Store store,
                                 FieldReferencePublicationFence publicationFence) {
        this(keyReferences, envelopeCodec, keyProtection, store,
                (encoded, context, target, expectedReference) -> {
                    byte[] plaintext = new ProtectedFieldCodec(envelopeCodec, keyProtection,
                            new SecureRandom(), expectedReference).unprotect(encoded, context, target);
                    Arrays.fill(plaintext, (byte) 0);
                }, publicationFence);
    }

    EnvelopeRewrapService(KeyReferenceRepository keyReferences,
                          EnvelopeCodec envelopeCodec,
                          KeyProtectionPort keyProtection,
                          Store store,
                          EnvelopeVerifier verifier,
                          FieldReferencePublicationFence publicationFence) {
        this.keyReferences = Objects.requireNonNull(keyReferences, "keyReferences");
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec");
        this.keyProtection = Objects.requireNonNull(keyProtection, "keyProtection");
        this.store = Objects.requireNonNull(store, "store");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.publicationFence = Objects.requireNonNull(publicationFence, "publicationFence");
    }

    public BatchResult rewrap(String oldKeyReference, int maximumRows) {
        String newKeyReference = activeKekReference();
        if (oldKeyReference == null || oldKeyReference.equals(newKeyReference) || maximumRows < 1) {
            throw failure();
        }
        long initial = store.checkpoint(oldKeyReference, newKeyReference);
        if (initial < 0) {
            throw failure();
        }
        long checkpoint = initial;
        int applied = 0;
        int already = 0;
        boolean exhausted = false;
        for (int index = 0; index < maximumRows; index++) {
            Optional<Candidate> next = store.next(oldKeyReference, checkpoint);
            if (next.isEmpty()) {
                exhausted = true;
                break;
            }
            Candidate candidate = next.get();
            if (candidate.sequence() <= checkpoint) {
                throw failure();
            }
            byte[] rewritten = rewrapCandidate(candidate, oldKeyReference, newKeyReference);
            byte[] rewrittenDigest = sha256(rewritten);
            try {
                AtomicInteger fenceInvocations = new AtomicInteger();
                CommitOutcome outcome;
                try {
                    outcome = store.replaceByOriginalDigestAndCheckpoint(oldKeyReference,
                            newKeyReference, candidate, rewritten, rewrittenDigest,
                            () -> {
                                if (fenceInvocations.incrementAndGet() != 1) {
                                    throw failure();
                                }
                                publicationFence.lockAndValidate(rewritten, candidate.target());
                            });
                } catch (RuntimeException failure) {
                    throw sanitized(failure);
                }
                if (fenceInvocations.get() != 1
                        || outcome == CommitOutcome.DRIFT || outcome == null) {
                    throw failure();
                }
                if (outcome == CommitOutcome.APPLIED) {
                    applied++;
                } else {
                    already++;
                }
                checkpoint = candidate.sequence();
            } finally {
                Arrays.fill(rewritten, (byte) 0);
                Arrays.fill(rewrittenDigest, (byte) 0);
            }
        }
        long durable = store.checkpoint(oldKeyReference, newKeyReference);
        if (durable != checkpoint) {
            throw failure();
        }
        return new BatchResult(initial, checkpoint, applied, already, exhausted);
    }

    private byte[] rewrapCandidate(Candidate candidate,
                                   String oldKeyReference,
                                   String newKeyReference) {
        byte[] encoded = candidate.encodedEnvelope();
        byte[] actualDigest = sha256(encoded);
        byte[] dek = null;
        byte[] oldHeader = null;
        byte[] newHeader = null;
        byte[] wrapNonce = null;
        byte[] wrappedDek = null;
        byte[] dataNonce = null;
        byte[] ciphertext = null;
        try {
            if (!MessageDigest.isEqual(actualDigest, candidate.originalEnvelopeDigest())) {
                throw failure();
            }
            CipherEnvelope oldEnvelope = envelopeCodec.decode(encoded, candidate.target());
            if (!"pkcs11".equals(oldEnvelope.providerId())
                    || !oldKeyReference.equals(oldEnvelope.keyReference())) {
                throw failure();
            }
            oldHeader = envelopeCodec.authenticatedHeader(oldEnvelope, candidate.target());
            dek = keyProtection.unwrap(new WrappedDataKey(oldEnvelope.keyReference(),
                    oldEnvelope.wrapNonce(), oldEnvelope.wrappedDek()), oldHeader, candidate.context());
            if (dek == null || dek.length != KeyProtectionPort.DATA_ENCRYPTION_KEY_BYTES) {
                throw failure();
            }

            ciphertext = oldEnvelope.ciphertext();
            long plaintextLength = ciphertext.length - EnvelopeCodec.DATA_TAG_BYTES;
            newHeader = envelopeCodec.authenticatedHeader(newKeyReference, plaintextLength,
                    candidate.target());
            WrappedDataKey replacement = keyProtection.wrap(dek, newHeader, candidate.context());
            if (replacement == null || !newKeyReference.equals(replacement.keyReference())) {
                throw failure();
            }
            wrapNonce = replacement.wrapNonce();
            wrappedDek = replacement.wrappedDek();
            dataNonce = oldEnvelope.dataNonce();
            CipherEnvelope rewritten = new CipherEnvelope(oldEnvelope.providerId(), newKeyReference,
                    wrapNonce, wrappedDek, dataNonce, ciphertext);
            byte[] output = envelopeCodec.encode(rewritten, candidate.target());

            CipherEnvelope decoded = envelopeCodec.decode(output, candidate.target());
            if (!Arrays.equals(dataNonce, decoded.dataNonce())
                    || !Arrays.equals(ciphertext, decoded.ciphertext())) {
                Arrays.fill(output, (byte) 0);
                throw failure();
            }
            verifier.verify(output, candidate.context(), candidate.target(), newKeyReference);
            return output;
        } catch (RuntimeException failure) {
            throw sanitized(failure);
        } finally {
            Arrays.fill(encoded, (byte) 0);
            Arrays.fill(actualDigest, (byte) 0);
            clear(dek);
            clear(oldHeader);
            clear(newHeader);
            clear(wrapNonce);
            clear(wrappedDek);
            clear(dataNonce);
            clear(ciphertext);
        }
    }

    private String activeKekReference() {
        KeyReferenceRepository.KeyReference active = keyReferences.uniqueActive(
                        KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK)
                .orElseThrow(EnvelopeRewrapService::failure);
        if (active.state() != KeyState.ACTIVE || !"pkcs11".equals(active.providerId())) {
            throw failure();
        }
        return active.providerKeyReference();
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("envelope digest unavailable");
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
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
