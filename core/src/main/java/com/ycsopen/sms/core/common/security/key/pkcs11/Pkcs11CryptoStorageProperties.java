package com.ycsopen.sms.core.common.security.key.pkcs11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Closed production configuration for one exact module, slot and key inventory. */
public final class Pkcs11CryptoStorageProperties {

    private final Path canonicalModulePath;
    private final long slotId;
    private final String tokenIdentity;
    private final Supplier<char[]> pinSource;
    private final List<Pkcs11KeyDescriptor> keys;

    public Pkcs11CryptoStorageProperties(Path modulePath,
                                         Collection<Path> allowedModulePaths,
                                         long slotId,
                                         String tokenIdentity,
                                         Supplier<char[]> pinSource,
                                         Collection<Pkcs11KeyDescriptor> keys) {
        this.canonicalModulePath = canonicalAllowedModule(modulePath, allowedModulePaths);
        if (slotId < 0 || tokenIdentity == null
                || !tokenIdentity.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("invalid PKCS11 token identity");
        }
        this.slotId = slotId;
        this.tokenIdentity = tokenIdentity;
        this.pinSource = Objects.requireNonNull(pinSource, "pinSource");
        this.keys = validateKeys(keys);
    }

    public Path canonicalModulePath() {
        return canonicalModulePath;
    }

    public long slotId() {
        return slotId;
    }

    String tokenIdentity() {
        return tokenIdentity;
    }

    char[] acquirePin() {
        char[] pin = pinSource.get();
        if (pin == null || pin.length < 4 || pin.length > 128) {
            throw new IllegalStateException("PKCS11 credential unavailable");
        }
        return pin;
    }

    public List<Pkcs11KeyDescriptor> keys() {
        return keys;
    }

    private static Path canonicalAllowedModule(Path modulePath, Collection<Path> allowedPaths) {
        Objects.requireNonNull(modulePath, "modulePath");
        Objects.requireNonNull(allowedPaths, "allowedModulePaths");
        try {
            Path requested = modulePath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("PKCS11 module is not a canonical regular file");
            }
            Path canonical = requested.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!requested.equals(canonical)) {
                throw new IllegalArgumentException("PKCS11 module path is not canonical");
            }
            Set<Path> allowlist = new HashSet<>();
            for (Path allowed : allowedPaths) {
                if (allowed == null) {
                    throw new IllegalArgumentException("invalid PKCS11 module allowlist");
                }
                allowlist.add(allowed.toAbsolutePath().normalize()
                        .toRealPath(LinkOption.NOFOLLOW_LINKS));
            }
            if (!allowlist.contains(canonical)) {
                throw new IllegalArgumentException("PKCS11 module is not allowlisted");
            }
            return canonical;
        } catch (IOException exception) {
            throw new IllegalArgumentException("PKCS11 module is unavailable");
        }
    }

    private static List<Pkcs11KeyDescriptor> validateKeys(Collection<Pkcs11KeyDescriptor> input) {
        Objects.requireNonNull(input, "keys");
        List<Pkcs11KeyDescriptor> copy = List.copyOf(new ArrayList<>(input));
        Set<String> aliases = new HashSet<>();
        Set<String> versions = new HashSet<>();
        for (Pkcs11KeyDescriptor key : copy) {
            Objects.requireNonNull(key, "key");
            if (!aliases.add(key.alias().toLowerCase(Locale.ROOT))
                    || !versions.add(key.purpose() + ":" + key.keyVersion())) {
                throw new IllegalArgumentException("duplicate PKCS11 key identity");
            }
        }
        long wrappingKeys = copy.stream()
                .filter(key -> key.purpose() == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK
                        && key.state().permitsWrap())
                .count();
        for (Pkcs11KeyDescriptor.Purpose purpose : Pkcs11KeyDescriptor.Purpose.values()) {
            if (copy.stream().noneMatch(key -> key.purpose() == purpose)) {
                throw new IllegalArgumentException("missing PKCS11 key purpose");
            }
        }
        if (wrappingKeys != 1) {
            throw new IllegalArgumentException("ambiguous active wrapping key");
        }
        return copy;
    }
}
