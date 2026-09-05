package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;

import java.io.InputStream;

/**
 * Private storage boundary for already encrypted YCSE/v1 objects.
 *
 * <p>The contract deliberately has no bucket, original filename, ACL, URL, or direct-link operation.
 * Callers address objects only with adapter-generated opaque storage keys.</p>
 */
public interface PrivateObjectStorePort {

    StoredObjectMetadata put(ObjectPurpose purpose,
                             String mediaType,
                             InputStream ciphertext,
                             Long declaredContentLength);

    StoredCiphertext get(String storageKey, ObjectPurpose purpose);

    StoredObjectMetadata head(String storageKey, ObjectPurpose purpose);

    void delete(String storageKey, ObjectPurpose purpose);

    /** The closed set of protected registration-object purposes. */
    enum ObjectPurpose {
        BUSINESS_LICENSE(EnvelopeCodec.Target.BUSINESS_LICENSE),
        REPRESENTATIVE_ID_FRONT(EnvelopeCodec.Target.REPRESENTATIVE_ID_FRONT),
        REPRESENTATIVE_ID_BACK(EnvelopeCodec.Target.REPRESENTATIVE_ID_BACK),
        SHORT_LINK_DOMAIN_PROOF(EnvelopeCodec.Target.SHORT_LINK_DOMAIN_PROOF),
        TRADEMARK_PROOF(EnvelopeCodec.Target.TRADEMARK_PROOF);

        private final EnvelopeCodec.Target envelopeTarget;

        ObjectPurpose(EnvelopeCodec.Target envelopeTarget) {
            this.envelopeTarget = envelopeTarget;
        }

        public long maximumEnvelopeBytes() {
            return envelopeTarget.maximumEnvelopeBytes();
        }

        EnvelopeCodec.Target envelopeTarget() {
            return envelopeTarget;
        }
    }

    /** Immutable ciphertext response; byte storage is copied at every boundary. */
    final class StoredCiphertext {
        private final byte[] ciphertext;
        private final StoredObjectMetadata metadata;

        public StoredCiphertext(byte[] ciphertext, StoredObjectMetadata metadata) {
            if (ciphertext == null || metadata == null || ciphertext.length != metadata.size()) {
                throw Failure.invalidInput();
            }
            this.ciphertext = ciphertext.clone();
            this.metadata = metadata;
        }

        public byte[] ciphertext() {
            return ciphertext.clone();
        }

        public StoredObjectMetadata metadata() {
            return metadata;
        }

        @Override
        public String toString() {
            return "StoredCiphertext[bytes=[redacted], metadata=" + metadata + "]";
        }
    }

    /** Stable, sanitized failure boundary. Provider exception text is never retained. */
    final class Failure extends RuntimeException {
        public enum Category {
            OBJECT_INPUT_INVALID,
            OBJECT_POLICY_INVALID,
            OBJECT_INTEGRITY_INVALID,
            OBJECT_STORE_UNAVAILABLE
        }

        private final Category category;

        private Failure(Category category, String message) {
            super(message, null, false, false);
            this.category = category;
        }

        public Category category() {
            return category;
        }

        public static Failure invalidInput() {
            return new Failure(Category.OBJECT_INPUT_INVALID, "private object input is invalid");
        }

        public static Failure invalidPolicy() {
            return new Failure(Category.OBJECT_POLICY_INVALID, "private object policy is invalid");
        }

        public static Failure integrity() {
            return new Failure(Category.OBJECT_INTEGRITY_INVALID, "private object integrity check failed");
        }

        public static Failure unavailable() {
            return new Failure(Category.OBJECT_STORE_UNAVAILABLE, "private object store is unavailable");
        }
    }
}
