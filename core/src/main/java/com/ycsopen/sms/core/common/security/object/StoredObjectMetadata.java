package com.ycsopen.sms.core.common.security.object;

import java.util.Objects;

/** Immutable, non-body facts for a private ciphertext object. */
public final class StoredObjectMetadata {
    private final String storageKey;
    private final PrivateObjectStorePort.ObjectPurpose purpose;
    private final long size;
    private final String sha256;
    private final String mediaType;

    public StoredObjectMetadata(String storageKey,
                                PrivateObjectStorePort.ObjectPurpose purpose,
                                long size,
                                String sha256,
                                String mediaType) {
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.size = size;
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
    }

    public String storageKey() {
        return storageKey;
    }

    public PrivateObjectStorePort.ObjectPurpose purpose() {
        return purpose;
    }

    public long size() {
        return size;
    }

    public String sha256() {
        return sha256;
    }

    public String mediaType() {
        return mediaType;
    }

    @Override
    public String toString() {
        return "StoredObjectMetadata[storageKey=[redacted], purpose=" + purpose
                + ", size=" + size + ", sha256=[redacted], mediaType=" + mediaType + "]";
    }
}
