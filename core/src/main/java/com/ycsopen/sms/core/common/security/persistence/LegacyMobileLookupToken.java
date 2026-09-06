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

    private final long tenantId;
    private final byte[] historicalDigest;
    private final BlindIndexPort.OrderedIndexes globalBlacklistIndexes;
    private final BlindIndexPort.OrderedIndexes tenantBlacklistIndexes;
    private final BlindIndexPort.OrderedIndexes portabilityIndexes;

    LegacyMobileLookupToken(long tenantId,
                            byte[] historicalDigest,
                            BlindIndexPort.OrderedIndexes globalBlacklistIndexes,
                            BlindIndexPort.OrderedIndexes tenantBlacklistIndexes,
                            BlindIndexPort.OrderedIndexes portabilityIndexes) {
        if (tenantId <= 0 || historicalDigest == null
                || historicalDigest.length != HISTORICAL_DIGEST_BYTES) {
            throw new IllegalArgumentException("invalid legacy lookup capability");
        }
        this.tenantId = tenantId;
        this.historicalDigest = historicalDigest.clone();
        this.globalBlacklistIndexes = copyIndexes(globalBlacklistIndexes);
        this.tenantBlacklistIndexes = copyIndexes(tenantBlacklistIndexes);
        this.portabilityIndexes = copyIndexes(portabilityIndexes);
    }

    LegacyMobileLookupToken defensiveCopy() {
        return new LegacyMobileLookupToken(tenantId, historicalDigest,
                globalBlacklistIndexes, tenantBlacklistIndexes, portabilityIndexes);
    }

    long tenantId() {
        return tenantId;
    }

    byte[] copyDigestForLegacyRead() {
        return historicalDigest.clone();
    }

    BlindIndexPort.OrderedIndexes globalBlacklistIndexes() {
        return copyIndexes(globalBlacklistIndexes);
    }

    BlindIndexPort.OrderedIndexes tenantBlacklistIndexes() {
        return copyIndexes(tenantBlacklistIndexes);
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
