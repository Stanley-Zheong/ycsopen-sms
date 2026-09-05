package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;

/**
 * Transactional publication fence for an opaque-token digest reference.
 *
 * <p>Implementations must lock the complete key-purpose set before validating the digest version,
 * and the caller must retain that lock until its business-row insert commits.</p>
 */
@FunctionalInterface
public interface TokenDigestPublicationFence {

    String SANITIZED_FAILURE = "token digest publication rejected";

    void lockAndValidate(VersionedTokenDigest digest);
}
