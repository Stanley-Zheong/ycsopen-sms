package com.ycsopen.sms.core.common.security.key.pkcs11;

import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Closed non-secret descriptor inventory whose lifecycle state is owned by the database. */
public final class VersionedKeyDescriptorRegistry {

    private final KeyReferenceRepository references;
    private final List<Pkcs11KeyDescriptor> configured;

    public VersionedKeyDescriptorRegistry(
            KeyReferenceRepository references, List<Pkcs11KeyDescriptor> configured) {
        this.references = Objects.requireNonNull(references, "references");
        this.configured = List.copyOf(Objects.requireNonNull(configured, "configured"));
        if (this.configured.isEmpty()) {
            throw rejected();
        }
    }

    public List<Pkcs11KeyDescriptor> load() {
        Map<Identity, Pkcs11KeyDescriptor> identities = new HashMap<>();
        for (Pkcs11KeyDescriptor descriptor : configured) {
            if (identities.put(new Identity(descriptor.purpose(), descriptor.keyVersion()), descriptor)
                    != null) {
                throw rejected();
            }
        }
        List<KeyReferenceRepository.KeyReference> stored = references.findAll();
        if (stored.size() != identities.size()) {
            throw rejected();
        }
        List<Pkcs11KeyDescriptor> resolved = new ArrayList<>();
        for (KeyReferenceRepository.KeyReference reference : stored) {
            Pkcs11KeyDescriptor configuredDescriptor = identities.remove(new Identity(
                    Pkcs11KeyDescriptor.Purpose.valueOf(reference.purpose().name()),
                    reference.keyVersion()));
            if (configuredDescriptor == null || !"pkcs11".equals(reference.providerId())
                    || !configuredDescriptor.keyReference().equals(reference.providerKeyReference())) {
                throw rejected();
            }
            resolved.add(new Pkcs11KeyDescriptor(configuredDescriptor.purpose(),
                    configuredDescriptor.keyVersion(), configuredDescriptor.keyReference(),
                    configuredDescriptor.alias(),
                    Pkcs11KeyDescriptor.State.valueOf(reference.state().name()),
                    configuredDescriptor.algorithm(), configuredDescriptor.keyBits()));
        }
        if (!identities.isEmpty()) {
            throw rejected();
        }
        return resolved.stream()
                .sorted(java.util.Comparator.comparing(Pkcs11KeyDescriptor::purpose)
                        .thenComparingLong(Pkcs11KeyDescriptor::keyVersion))
                .toList();
    }

    /** Parses comma-separated PURPOSE|version|reference|alias identities; state always comes from DB. */
    public static List<Pkcs11KeyDescriptor> configured(
            String encoded, List<Pkcs11KeyDescriptor> fallback) {
        if (encoded == null || encoded.isBlank()) {
            return List.copyOf(Objects.requireNonNull(fallback, "fallback"));
        }
        List<Pkcs11KeyDescriptor> descriptors = new ArrayList<>();
        for (String entry : encoded.split(",", -1)) {
            String[] fields = entry.split("\\|", -1);
            if (fields.length != 4 || entry.isBlank()) {
                throw rejected();
            }
            try {
                Pkcs11KeyDescriptor.Purpose purpose =
                        Pkcs11KeyDescriptor.Purpose.valueOf(fields[0]);
                descriptors.add(new Pkcs11KeyDescriptor(purpose, Long.parseLong(fields[1]),
                        fields[2], fields[3], Pkcs11KeyDescriptor.State.PREPARED,
                        purpose.isWrappingKey() ? "AES" : "HmacSHA256", 256));
            } catch (RuntimeException invalid) {
                throw rejected();
            }
        }
        return List.copyOf(descriptors);
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("PKCS11_DESCRIPTOR_REGISTRY_REJECTED");
    }

    private record Identity(Pkcs11KeyDescriptor.Purpose purpose, long version) {
    }
}
