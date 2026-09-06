package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;

/** Transactional fence preventing publication under a key that stopped accepting writes. */
@FunctionalInterface
public interface FieldReferencePublicationFence {

    String SANITIZED_FAILURE = "field reference publication rejected";

    /** Locks the complete FIELD purpose and returns the still-current key version. */
    long lockAndValidate(byte[] encodedEnvelope, EnvelopeCodec.Target target);
}
