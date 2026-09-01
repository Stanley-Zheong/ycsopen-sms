package com.ycsopen.sms.core.common.security.key.pkcs11;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Key;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates a closed SunPKCS11 session for one allowlisted module and exact slot. */
public final class Pkcs11ProviderFactory {

    private final ProviderConfigurator configurator;
    private final TokenLoader tokenLoader;
    private final Pkcs11FailureMapper failureMapper;

    public Pkcs11ProviderFactory(Pkcs11FailureMapper failureMapper) {
        this((config, expectedProviderName) -> {
                    Provider base = Security.getProvider("SunPKCS11");
                    if (base == null) {
                        throw new IllegalStateException("SunPKCS11 unavailable");
                    }
                    Provider configured = base.configure(config.toString());
                    if (!expectedProviderName.equals(configured.getName())) {
                        throw new IllegalStateException("unexpected SunPKCS11 provider");
                    }
                    return configured;
                }, Pkcs11ProviderFactory::loadToken, failureMapper);
    }

    Pkcs11ProviderFactory(ProviderConfigurator configurator,
                          TokenLoader tokenLoader,
                          Pkcs11FailureMapper failureMapper) {
        this.configurator = Objects.requireNonNull(configurator, "configurator");
        this.tokenLoader = Objects.requireNonNull(tokenLoader, "tokenLoader");
        this.failureMapper = Objects.requireNonNull(failureMapper, "failureMapper");
    }

    public Session open(Pkcs11CryptoStorageProperties properties) {
        Objects.requireNonNull(properties, "properties");
        Path config = null;
        char[] pin = null;
        try {
            String providerSuffix = providerSuffix(properties);
            String expectedProviderName = "SunPKCS11-" + providerSuffix;
            config = writeConfiguration(properties, providerSuffix);
            Provider provider = configurator.configure(config, expectedProviderName);
            if (provider == null || !expectedProviderName.equals(provider.getName())) {
                throw new IllegalStateException("provider identity mismatch");
            }
            pin = properties.acquirePin();
            Map<String, TokenKey> keys = tokenLoader.load(provider, pin, properties.keys());
            validateInventory(keys, properties.keys());
            return new Session(provider, keys, tokenIdentityHash(properties));
        } catch (Pkcs11FailureMapper.Pkcs11OperationException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            throw failureMapper.failure(Pkcs11FailureMapper.Category.CONFIGURATION, null, exception);
        } finally {
            if (pin != null) {
                Arrays.fill(pin, '\0');
            }
            if (config != null) {
                try {
                    Files.deleteIfExists(config);
                } catch (IOException ignored) {
                    // Configuration is secret-free; failure remains redacted and fail closed above.
                }
            }
        }
    }

    private static Path writeConfiguration(Pkcs11CryptoStorageProperties properties,
                                           String providerSuffix) throws IOException {
        String library = properties.canonicalModulePath().toString();
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(library)
                || library.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("invalid PKCS11 module path");
        }
        Path config = Files.createTempFile("ycsopen-sunpkcs11-", ".cfg");
        try {
            Files.setPosixFilePermissions(config, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX systems retain the platform's owner-scoped temp-file policy.
        }
        String contents = "name=" + providerSuffix + "\n"
                + "library=" + library + "\n"
                + "slot=" + properties.slotId() + "\n"
                + "enabledMechanisms={ 0x7FFFFF21 CKM_AES_KEY_GEN CKM_AES_GCM "
                + "CKM_GENERIC_SECRET_KEY_GEN CKM_SHA256_HMAC }\n";
        Files.writeString(config, contents, StandardCharsets.US_ASCII);
        return config;
    }

    private static Map<String, TokenKey> loadToken(Provider provider,
                                                    char[] pin,
                                                    java.util.List<Pkcs11KeyDescriptor> descriptors) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
            keyStore.load(null, pin);
            Map<String, TokenKey> keys = new HashMap<>();
            Enumeration<String> aliases = keyStore.aliases();
            Set<String> seenAliases = new HashSet<>();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!seenAliases.add(alias.toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("duplicate token alias");
                }
            }
            for (Pkcs11KeyDescriptor descriptor : descriptors) {
                if (!keyStore.containsAlias(descriptor.alias()) || !keyStore.isKeyEntry(descriptor.alias())) {
                    throw new IllegalStateException("missing token key");
                }
                Key key = keyStore.getKey(descriptor.alias(), null);
                if (!(key instanceof SecretKey secretKey)) {
                    throw new IllegalStateException("wrong token key class");
                }
                keys.put(descriptor.alias(), inspect(provider, secretKey));
            }
            return keys;
        } catch (Exception exception) {
            throw new IllegalStateException("token load failed");
        }
    }

    private static TokenKey inspect(Provider provider, SecretKey key) {
        Module module = key.getClass().getModule();
        if (module == null || !"jdk.crypto.cryptoki".equals(module.getName())
                || !key.getClass().getName().startsWith("sun.security.pkcs11.P11Key$")
                || key.getFormat() != null) {
            throw new IllegalStateException("key is not an opaque SunPKCS11 handle");
        }
        Pattern description = Pattern.compile("^" + Pattern.quote(provider.getName())
                + " (AES|Generic Secret|HmacSHA256) secret key, (\\d+) bits token object, "
                + "sensitive, unextractable\\)$");
        Matcher matcher = description.matcher(key.toString());
        if (!matcher.matches()) {
            throw new IllegalStateException("token key attributes unavailable");
        }
        return new TokenKey(key, matcher.group(1), normalizeP11SecretKeyBits(matcher.group(2)),
                true, true, false);
    }

    static int normalizeP11SecretKeyBits(String valueLengthBytes) {
        try {
            int value = Integer.parseInt(valueLengthBytes);
            if (value <= 0) {
                throw new IllegalArgumentException("invalid token key length");
            }
            return Math.multiplyExact(value, Byte.SIZE);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("invalid token key length", exception);
        }
    }

    private static void validateInventory(Map<String, TokenKey> keys,
                                          java.util.List<Pkcs11KeyDescriptor> descriptors) {
        if (keys == null || keys.size() != descriptors.size()) {
            throw new IllegalStateException("token key inventory mismatch");
        }
        Set<String> normalized = new HashSet<>();
        for (Pkcs11KeyDescriptor descriptor : descriptors) {
            if (!normalized.add(descriptor.alias().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("duplicate requested alias");
            }
            TokenKey key = keys.get(descriptor.alias());
            if (key == null || !algorithmMatches(descriptor, key.algorithm())
                    || key.keyBits() != descriptor.keyBits() || !key.tokenObject()
                    || !key.sensitive() || key.extractable()) {
                throw new IllegalStateException("token key policy mismatch");
            }
        }
    }

    private static boolean algorithmMatches(Pkcs11KeyDescriptor descriptor, String actual) {
        if (descriptor.purpose() == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK) {
            return "AES".equalsIgnoreCase(actual);
        }
        return "Generic Secret".equalsIgnoreCase(actual)
                || "HmacSHA256".equalsIgnoreCase(actual);
    }

    private static String providerSuffix(Pkcs11CryptoStorageProperties properties) {
        return "YcsOpen" + tokenIdentityHash(properties).substring(0, 16);
    }

    private static String tokenIdentityHash(Pkcs11CryptoStorageProperties properties) {
        try {
            String identity = properties.canonicalModulePath() + "\0" + properties.slotId()
                    + "\0" + properties.tokenIdentity();
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("token identity hash unavailable");
        }
    }

    @FunctionalInterface
    interface ProviderConfigurator {
        Provider configure(Path config, String expectedProviderName) throws IOException;
    }

    @FunctionalInterface
    interface TokenLoader {
        Map<String, TokenKey> load(Provider provider,
                                   char[] pin,
                                   java.util.List<Pkcs11KeyDescriptor> descriptors);
    }

    record TokenKey(SecretKey handle,
                    String algorithm,
                    int keyBits,
                    boolean tokenObject,
                    boolean sensitive,
                    boolean extractable) {
        TokenKey {
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(algorithm, "algorithm");
        }
    }

    public static final class Session implements AutoCloseable {
        private final Provider provider;
        private final Map<String, TokenKey> keys;
        private final String tokenIdentityHash;

        private Session(Provider provider, Map<String, TokenKey> keys, String tokenIdentityHash) {
            this.provider = Objects.requireNonNull(provider, "provider");
            this.keys = Collections.unmodifiableMap(new HashMap<>(keys));
            this.tokenIdentityHash = Objects.requireNonNull(tokenIdentityHash, "tokenIdentityHash");
        }

        Provider provider() {
            return provider;
        }

        TokenKey key(String alias) {
            return keys.get(alias);
        }

        public String tokenIdentityHash() {
            return tokenIdentityHash;
        }

        @Override
        public void close() {
            // Provider instances are not globally registered; token lifetime follows their handles.
        }
    }
}
