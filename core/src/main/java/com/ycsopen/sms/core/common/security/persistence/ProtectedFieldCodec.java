package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.envelope.ProtectionFailure;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Per-value YCSE/v1 protection boundary for persistence adapters.
 *
 * <p>The configured key reference is public envelope metadata, never key material. It lets this
 * codec authenticate the complete header before the one opaque wrap call; the key adapter must
 * return the same canonical reference or the operation fails closed.</p>
 */
public final class ProtectedFieldCodec {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String PROVIDER_ID = "pkcs11";

    private final EnvelopeCodec envelopeCodec;
    private final KeyProtectionPort keyProtectionPort;
    private final SecureRandom secureRandom;
    private final String keyReference;

    public ProtectedFieldCodec(EnvelopeCodec envelopeCodec,
                               KeyProtectionPort keyProtectionPort,
                               SecureRandom secureRandom,
                               String keyReference) {
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec");
        this.keyProtectionPort = Objects.requireNonNull(keyProtectionPort, "keyProtectionPort");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.keyReference = requireCanonicalKeyReference(keyReference);
    }

    /** Protects exactly one bounded value as one strict YCSE/v1 envelope. */
    public byte[] protect(byte[] plaintext,
                          ProtectionContext semanticContext,
                          EnvelopeCodec.Target target) {
        return protect(plaintext, plaintext == null ? -1 : plaintext.length, semanticContext, target);
    }

    /**
     * Protects one nonempty prefix without copying it. This is the bounded streaming entrypoint
     * for callers that own a fixed-size reusable chunk buffer.
     */
    public byte[] protect(byte[] plaintext,
                          int plaintextLength,
                          ProtectionContext semanticContext,
                          EnvelopeCodec.Target target) {
        byte[] dataEncryptionKey = null;
        byte[] dataNonce = null;
        byte[] authenticatedHeader = null;
        byte[] dataAad = null;
        byte[] ciphertext = null;
        byte[] wrapNonce = null;
        byte[] wrappedDek = null;
        try {
            if (plaintext == null || plaintextLength < 1 || plaintextLength > plaintext.length
                    || semanticContext == null || target == null) {
                throw invalid(target);
            }

            // Enforce both the selected plaintext ceiling and complete-envelope bound before JCE
            // or the adapter can consume a durable wrap reservation.
            envelopeCodec.maximumCompleteEnvelopeLength(keyReference, plaintextLength, target);
            authenticatedHeader = envelopeCodec.authenticatedHeader(
                    keyReference, plaintextLength, target);
            dataAad = envelopeCodec.dataAad(
                    keyReference, plaintextLength, semanticContext, target);

            dataEncryptionKey = new byte[KeyProtectionPort.DATA_ENCRYPTION_KEY_BYTES];
            dataNonce = new byte[EnvelopeCodec.NONCE_BYTES];
            secureRandom.nextBytes(dataEncryptionKey);
            secureRandom.nextBytes(dataNonce);
            ciphertext = encrypt(
                    plaintext, plaintextLength, dataEncryptionKey, dataNonce, dataAad);

            // The adapter owns admission, KEK selection, wrap nonce generation and provider use.
            WrappedDataKey wrappedDataKey = keyProtectionPort.wrap(
                    dataEncryptionKey, authenticatedHeader, semanticContext);
            if (wrappedDataKey == null || !keyReference.equals(wrappedDataKey.keyReference())) {
                throw invalid(target);
            }
            wrapNonce = wrappedDataKey.wrapNonce();
            wrappedDek = wrappedDataKey.wrappedDek();

            CipherEnvelope envelope = new CipherEnvelope(
                    PROVIDER_ID, wrappedDataKey.keyReference(), wrapNonce, wrappedDek, dataNonce, ciphertext);
            return envelopeCodec.encode(envelope, target);
        } catch (ProtectionFailure failure) {
            throw failure;
        } catch (RuntimeException | GeneralSecurityException failure) {
            throw invalid(target);
        } finally {
            clear(dataEncryptionKey);
            clear(dataNonce);
            clear(authenticatedHeader);
            clear(dataAad);
            clear(ciphertext);
            clear(wrapNonce);
            clear(wrappedDek);
        }
    }

    /** Strictly authenticates and returns one protected value with no legacy/plaintext fallback. */
    public byte[] unprotect(byte[] encodedEnvelope,
                            ProtectionContext semanticContext,
                            EnvelopeCodec.Target target) {
        byte[] authenticatedHeader = null;
        byte[] dataAad = null;
        byte[] dataEncryptionKey = null;
        byte[] wrapNonce = null;
        byte[] wrappedDek = null;
        byte[] dataNonce = null;
        byte[] ciphertext = null;
        try {
            if (semanticContext == null || target == null) {
                throw invalid(target);
            }
            CipherEnvelope envelope = envelopeCodec.decode(encodedEnvelope, target);
            authenticatedHeader = envelopeCodec.authenticatedHeader(envelope, target);
            dataAad = envelopeCodec.dataAad(envelope, semanticContext, target);
            wrapNonce = envelope.wrapNonce();
            wrappedDek = envelope.wrappedDek();
            dataNonce = envelope.dataNonce();
            ciphertext = envelope.ciphertext();

            WrappedDataKey wrappedDataKey = new WrappedDataKey(
                    envelope.keyReference(), wrapNonce, wrappedDek);
            dataEncryptionKey = keyProtectionPort.unwrap(
                    wrappedDataKey, authenticatedHeader, semanticContext);
            if (dataEncryptionKey == null
                    || dataEncryptionKey.length != KeyProtectionPort.DATA_ENCRYPTION_KEY_BYTES) {
                throw invalid(target);
            }
            return decrypt(ciphertext, dataEncryptionKey, dataNonce, dataAad);
        } catch (ProtectionFailure failure) {
            throw failure;
        } catch (RuntimeException | GeneralSecurityException failure) {
            throw invalid(target);
        } finally {
            clear(authenticatedHeader);
            clear(dataAad);
            clear(dataEncryptionKey);
            clear(wrapNonce);
            clear(wrappedDek);
            clear(dataNonce);
            clear(ciphertext);
        }
    }

    private static byte[] encrypt(byte[] plaintext,
                                  int plaintextLength,
                                  byte[] dataEncryptionKey,
                                  byte[] dataNonce,
                                  byte[] dataAad) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataEncryptionKey, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, dataNonce));
        cipher.updateAAD(dataAad);
        return cipher.doFinal(plaintext, 0, plaintextLength);
    }

    private static byte[] decrypt(byte[] ciphertext,
                                  byte[] dataEncryptionKey,
                                  byte[] dataNonce,
                                  byte[] dataAad) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataEncryptionKey, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, dataNonce));
        cipher.updateAAD(dataAad);
        return cipher.doFinal(ciphertext);
    }

    private String requireCanonicalKeyReference(String candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("key reference is required");
        }
        try {
            envelopeCodec.authenticatedHeader(candidate, 0, EnvelopeCodec.Target.DATABASE_FIELD);
            return candidate;
        } catch (ProtectionFailure failure) {
            throw new IllegalArgumentException("key reference is invalid");
        }
    }

    private ProtectionFailure invalid(EnvelopeCodec.Target target) {
        try {
            envelopeCodec.decode((byte[]) null, target);
        } catch (ProtectionFailure failure) {
            return failure;
        }
        throw new IllegalStateException("unreachable protected-data failure path");
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }
}
