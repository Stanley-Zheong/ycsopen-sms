package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable result of preparing one message mobile for persistence and opaque routing.
 * Ciphertext and index values are never rendered and every binary accessor returns a copy.
 */
public final class PreparedMessageMobile {

    private static final int MOBILE_CIPHERTEXT_BYTES = 11 + EnvelopeCodec.DATA_TAG_BYTES;

    private final long tenantId;
    private final String messageId;
    private final byte[] envelope;
    private final String legacyLocator;
    private final BlindIndexPort.OrderedIndexes writeIndexes;
    private final BlindIndexPort.OrderedIndexes queryIndexes;
    private final LegacyMobileLookupToken legacyLookupToken;

    PreparedMessageMobile(long tenantId,
                          String messageId,
                          byte[] envelope,
                          String legacyLocator,
                          BlindIndexPort.OrderedIndexes writeIndexes,
                          BlindIndexPort.OrderedIndexes queryIndexes,
                          LegacyMobileLookupToken legacyLookupToken) {
        if (tenantId <= 0 || messageId == null || messageId.isEmpty()
                || envelope == null || legacyLocator == null
                || !MessageTaskRowBinding.isCurrentLocator(legacyLocator)) {
            throw new IllegalArgumentException("invalid prepared message mobile");
        }
        CipherEnvelope decoded = new EnvelopeCodec().decode(envelope, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] ciphertext = decoded.ciphertext();
        try {
            if (ciphertext.length != MOBILE_CIPHERTEXT_BYTES) {
                throw new IllegalArgumentException("invalid prepared message mobile");
            }
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
        }
        this.writeIndexes = copyIndexes(writeIndexes);
        this.queryIndexes = copyIndexes(queryIndexes);
        this.legacyLookupToken = Objects.requireNonNull(
                legacyLookupToken, "legacyLookupToken").defensiveCopy();
        if (!this.queryIndexes.values().containsAll(this.writeIndexes.values())) {
            throw new IllegalArgumentException("write indexes are not query compatible");
        }
        this.tenantId = tenantId;
        this.messageId = messageId;
        this.envelope = envelope.clone();
        this.legacyLocator = legacyLocator;
    }

    public byte[] copyEnvelope() {
        return envelope.clone();
    }

    public String legacyLocator() {
        return legacyLocator;
    }

    public BlindIndexPort.OrderedIndexes queryIndexes() {
        return queryIndexes;
    }

    public LegacyMobileLookupToken legacyLookupToken() {
        return legacyLookupToken.defensiveCopy();
    }

    long tenantId() {
        return tenantId;
    }

    String messageId() {
        return messageId;
    }

    BlindIndexPort.OrderedIndexes writeIndexes() {
        return writeIndexes;
    }

    byte[] originalRowDigest(long legacyRowId) {
        if (legacyRowId <= 0) {
            throw new IllegalArgumentException("invalid legacy row binding");
        }
        return MessageTaskRowBinding.originalRowDigest(
                tenantId, legacyRowId, messageId, legacyLocator, envelope);
    }

    private static BlindIndexPort.OrderedIndexes copyIndexes(
            BlindIndexPort.OrderedIndexes indexes) {
        Objects.requireNonNull(indexes, "indexes");
        for (VersionedBlindIndex value : indexes.values()) {
            Objects.requireNonNull(value, "index");
        }
        return new BlindIndexPort.OrderedIndexes(indexes.values());
    }

    @Override
    public String toString() {
        return "PreparedMessageMobile[tenant=[redacted], message=[redacted], envelope=[redacted], "
                + "locator=[redacted], writeIndexes=[redacted], queryIndexes=[redacted]]";
    }
}
