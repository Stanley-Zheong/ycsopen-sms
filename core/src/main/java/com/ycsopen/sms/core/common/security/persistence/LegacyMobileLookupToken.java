package com.ycsopen.sms.core.common.security.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;

import java.util.Objects;

/**
 * Opaque, non-serializable compatibility capability created while a normalized mobile is
 * transiently available. It exposes neither the historical digest nor target query values.
 */
@JsonIgnoreType
public final class LegacyMobileLookupToken {

    private static final int HISTORICAL_DIGEST_BYTES = 32;

    private final byte[] historicalDigest;
    private final BlindIndexPort.OrderedIndexes blacklistIndexes;
    private final BlindIndexPort.OrderedIndexes portabilityIndexes;

    LegacyMobileLookupToken(byte[] historicalDigest,
                            BlindIndexPort.OrderedIndexes blacklistIndexes,
                            BlindIndexPort.OrderedIndexes portabilityIndexes) {
        if (historicalDigest == null || historicalDigest.length != HISTORICAL_DIGEST_BYTES) {
            throw new IllegalArgumentException("invalid legacy lookup capability");
        }
        this.historicalDigest = historicalDigest.clone();
        this.blacklistIndexes = copyIndexes(blacklistIndexes);
        this.portabilityIndexes = copyIndexes(portabilityIndexes);
    }

    LegacyMobileLookupToken defensiveCopy() {
        return new LegacyMobileLookupToken(historicalDigest, blacklistIndexes, portabilityIndexes);
    }

    byte[] copyDigestForLegacyRead() {
        return historicalDigest.clone();
    }

    BlindIndexPort.OrderedIndexes blacklistIndexes() {
        return copyIndexes(blacklistIndexes);
    }

    BlindIndexPort.OrderedIndexes portabilityIndexes() {
        return copyIndexes(portabilityIndexes);
    }

    private static BlindIndexPort.OrderedIndexes copyIndexes(BlindIndexPort.OrderedIndexes indexes) {
        Objects.requireNonNull(indexes, "indexes");
        return new BlindIndexPort.OrderedIndexes(indexes.values());
    }

    @Override
    public String toString() {
        return "LegacyMobileLookupToken[digest=[redacted], indexes=[redacted]]";
    }
}
