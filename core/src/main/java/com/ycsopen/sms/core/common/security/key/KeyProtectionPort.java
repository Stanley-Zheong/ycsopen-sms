package com.ycsopen.sms.core.common.security.key;

import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;

/**
 * Opaque boundary for protecting per-value data-encryption keys.
 *
 * <p>The active KEK, durable wrap admission, and wrap nonce all belong to the
 * adapter. Callers provide only the DEK and authenticated context, so they
 * cannot reserve, reuse, or supply a wrap nonce.</p>
 */
public interface KeyProtectionPort {

    int DATA_ENCRYPTION_KEY_BYTES = 32;

    WrappedDataKey wrap(byte[] dataEncryptionKey,
                        byte[] authenticatedHeader,
                        ProtectionContext semanticContext);

    byte[] unwrap(WrappedDataKey wrappedDataKey,
                  byte[] authenticatedHeader,
                  ProtectionContext semanticContext);

    KeyHealth health();
}
