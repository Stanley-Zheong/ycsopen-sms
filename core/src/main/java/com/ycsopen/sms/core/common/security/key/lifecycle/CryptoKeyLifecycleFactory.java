package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Production composition seam for lifecycle and rewrap; intentionally has no provision/delete API. */
public final class CryptoKeyLifecycleFactory {

    private final KeyReferenceRepository references;
    private final KeyProtectionPort keyProtection;

    public CryptoKeyLifecycleFactory(
            KeyReferenceRepository references, KeyProtectionPort keyProtection) {
        this.references = Objects.requireNonNull(references, "references");
        this.keyProtection = Objects.requireNonNull(keyProtection, "keyProtection");
    }

    public KeyLifecycleService lifecycle(
            Set<String> requiredSources, List<EnvelopeReferenceInventory.Source> sources) {
        return new KeyLifecycleService(references,
                new EnvelopeReferenceInventory(requiredSources, sources));
    }

    public EnvelopeRewrapService rewrap(EnvelopeRewrapService.Store store) {
        return new EnvelopeRewrapService(references, new EnvelopeCodec(), keyProtection,
                Objects.requireNonNull(store, "store"));
    }
}
