package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;

import java.util.Objects;

/**
 * Opaque, immutable mobile material that may cross the pre-persistence routing boundary.
 *
 * <p>The capability deliberately contains no mobile plaintext, field envelope, locator, or
 * wrapping-key result. A persistence envelope is created only after routing accepts the request.</p>
 */
public final class PreparedMessageRouting {

    private final long tenantId;
    private final String messageId;
    private final BlindIndexPort.OrderedIndexes writeIndexes;
    private final BlindIndexPort.OrderedIndexes queryIndexes;
    private final LegacyMobileLookupToken legacyLookupToken;

    PreparedMessageRouting(long tenantId,
                           String messageId,
                           BlindIndexPort.OrderedIndexes writeIndexes,
                           BlindIndexPort.OrderedIndexes queryIndexes,
                           LegacyMobileLookupToken legacyLookupToken) {
        if (tenantId <= 0 || messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("invalid prepared message routing");
        }
        this.writeIndexes = copyIndexes(writeIndexes);
        this.queryIndexes = copyIndexes(queryIndexes);
        this.legacyLookupToken = Objects.requireNonNull(
                legacyLookupToken, "legacyLookupToken").defensiveCopy();
        if (this.legacyLookupToken.tenantId() != tenantId
                || !this.queryIndexes.values().containsAll(this.writeIndexes.values())) {
            throw new IllegalArgumentException("invalid prepared message routing");
        }
        this.tenantId = tenantId;
        this.messageId = messageId;
    }

    public BlindIndexPort.OrderedIndexes queryIndexes() {
        return copyIndexes(queryIndexes);
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
        return copyIndexes(writeIndexes);
    }

    LegacyMobileLookupToken internalLookupToken() {
        return legacyLookupToken;
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
        return "PreparedMessageRouting[tenant=[redacted], message=[redacted], "
                + "writeIndexes=[redacted], queryIndexes=[redacted], lookup=[redacted]]";
    }
}
