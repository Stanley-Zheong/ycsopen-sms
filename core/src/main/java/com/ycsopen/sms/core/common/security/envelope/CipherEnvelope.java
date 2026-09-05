package com.ycsopen.sms.core.common.security.envelope;

import java.util.Arrays;
import java.util.Objects;

/** Immutable YCSE/v1 values. Binary components are copied at every boundary. */
public final class CipherEnvelope {

    private final String providerId;
    private final String keyReference;
    private final byte[] wrapNonce;
    private final byte[] wrappedDek;
    private final byte[] dataNonce;
    private final byte[] ciphertext;

    public CipherEnvelope(String providerId,
                          String keyReference,
                          byte[] wrapNonce,
                          byte[] wrappedDek,
                          byte[] dataNonce,
                          byte[] ciphertext) {
        EnvelopeCodec.requireProvider(providerId);
        EnvelopeCodec.requireKeyReference(keyReference);
        EnvelopeCodec.requireExactLength(wrapNonce, EnvelopeCodec.NONCE_BYTES);
        EnvelopeCodec.requireExactLength(wrappedDek, EnvelopeCodec.WRAPPED_DEK_BYTES);
        EnvelopeCodec.requireExactLength(dataNonce, EnvelopeCodec.NONCE_BYTES);
        EnvelopeCodec.requireCiphertext(ciphertext);
        this.providerId = providerId;
        this.keyReference = keyReference;
        this.wrapNonce = wrapNonce.clone();
        this.wrappedDek = wrappedDek.clone();
        this.dataNonce = dataNonce.clone();
        this.ciphertext = ciphertext.clone();
    }

    public String providerId() {
        return providerId;
    }

    public String keyReference() {
        return keyReference;
    }

    public byte[] wrapNonce() {
        return wrapNonce.clone();
    }

    public byte[] wrappedDek() {
        return wrappedDek.clone();
    }

    public byte[] dataNonce() {
        return dataNonce.clone();
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    int ciphertextLength() {
        return ciphertext.length;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CipherEnvelope that)) {
            return false;
        }
        return providerId.equals(that.providerId)
                && keyReference.equals(that.keyReference)
                && Arrays.equals(wrapNonce, that.wrapNonce)
                && Arrays.equals(wrappedDek, that.wrappedDek)
                && Arrays.equals(dataNonce, that.dataNonce)
                && Arrays.equals(ciphertext, that.ciphertext);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(providerId, keyReference);
        result = 31 * result + Arrays.hashCode(wrapNonce);
        result = 31 * result + Arrays.hashCode(wrappedDek);
        result = 31 * result + Arrays.hashCode(dataNonce);
        result = 31 * result + Arrays.hashCode(ciphertext);
        return result;
    }

    @Override
    public String toString() {
        return "CipherEnvelope[providerId=" + providerId + ", keyReference=[redacted], bytes=[redacted]]";
    }
}
