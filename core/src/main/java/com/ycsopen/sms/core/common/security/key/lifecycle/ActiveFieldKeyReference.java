package com.ycsopen.sms.core.common.security.key.lifecycle;

import java.util.Objects;

/** Resolves the database-owned ACTIVE field key for construction of a restarted writer. */
public final class ActiveFieldKeyReference {

    private final KeyReferenceRepository references;

    public ActiveFieldKeyReference(KeyReferenceRepository references) {
        this.references = Objects.requireNonNull(references, "references");
    }

    public String current() {
        KeyReferenceRepository.KeyReference active = references.uniqueActive(
                        KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK)
                .orElseThrow(ActiveFieldKeyReference::rejected);
        if (!"pkcs11".equals(active.providerId()) || !active.state().permitsWrap()) {
            throw rejected();
        }
        return active.providerKeyReference();
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("ACTIVE_FIELD_KEY_REFERENCE_REJECTED");
    }
}
