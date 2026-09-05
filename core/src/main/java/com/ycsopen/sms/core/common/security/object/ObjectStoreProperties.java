package com.ycsopen.sms.core.common.security.object;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Closed production object-store configuration. Credentials are selected by provider, never by value. */
@ConfigurationProperties(prefix = "ycsopen.object-store")
public record ObjectStoreProperties(boolean enabled,
                                    String bucket,
                                    String region,
                                    URI endpoint,
                                    Set<URI> allowedEndpoints,
                                    CredentialProvider credentialProvider,
                                    boolean pathStyleAccess,
                                    boolean allowInsecureLoopback) {
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");

    public ObjectStoreProperties {
        allowedEndpoints = allowedEndpoints == null ? Set.of() : Set.copyOf(allowedEndpoints);
        credentialProvider = credentialProvider == null ? CredentialProvider.DEFAULT_CHAIN : credentialProvider;
        if (enabled) {
            require(BUCKET.matcher(value(bucket)).matches());
            require(!value(region).isBlank());
            if (endpoint != null) {
                require(endpointIsCanonical(endpoint));
                require(allowedEndpoints.stream().anyMatch(endpoint::equals));
                if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
                    require(allowInsecureLoopback && isLoopback(endpoint));
                }
            }
        }
    }

    public enum CredentialProvider {
        DEFAULT_CHAIN,
        CONTAINER,
        INSTANCE_PROFILE
    }

    private static boolean endpointIsCanonical(URI endpoint) {
        String path = endpoint.getPath();
        return endpoint.isAbsolute()
                && endpoint.getHost() != null
                && endpoint.getUserInfo() == null
                && endpoint.getQuery() == null
                && endpoint.getFragment() == null
                && (path == null || path.isEmpty() || path.equals("/"));
    }

    private static boolean isLoopback(URI endpoint) {
        String host = endpoint.getHost().toLowerCase(Locale.ROOT);
        return host.equals("127.0.0.1") || host.equals("::1") || host.equals("localhost");
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("private object configuration is invalid");
        }
    }

    @Override
    public String toString() {
        return "ObjectStoreProperties[enabled=" + enabled + ", configuration=[redacted]]";
    }
}
