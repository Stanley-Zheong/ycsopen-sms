package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Issues and validates purpose-bound opaque application capabilities. */
public final class ObjectCapabilityService {

    static final String CAPABILITY_PATH_PREFIX = "/api/v1/protected-objects/capabilities/";
    private static final String TOKEN_PREFIX = "ocap_v1_";
    private static final int LOOKUP_BYTES = 16;
    private static final int LOOKUP_CHARACTERS = 22;
    private static final int SECRET_CHARACTERS = 43;
    private static final int ISSUE_ATTEMPTS = 4;
    private static final byte[] BINDING_DIGEST_DOMAIN =
            "YCS-OBJECT-CAPABILITY-BINDING/v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern COMPONENT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@/-]{0,255}");
    private static final Pattern PURPOSE = Pattern.compile("[a-z][a-z0-9-]{0,39}");
    private static final Pattern LOOKUP = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final OpaqueTokenDigestPort tokenDigestPort;
    private final CapabilityStore capabilityStore;
    private final ObjectAccessAuthorizationPort authorizationPort;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public ObjectCapabilityService(OpaqueTokenDigestPort tokenDigestPort,
                                   CapabilityStore capabilityStore,
                                   ObjectAccessAuthorizationPort authorizationPort,
                                   Clock clock,
                                   SecureRandom secureRandom) {
        this.tokenDigestPort = Objects.requireNonNull(tokenDigestPort, "tokenDigestPort");
        this.capabilityStore = Objects.requireNonNull(capabilityStore, "capabilityStore");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    /** Issues one path while retaining only its versioned keyed digest and bindings. */
    public ObjectCapabilityToken issue(IssueRequest request) {
        requireIssueRequest(request);
        for (int attempt = 0; attempt < ISSUE_ATTEMPTS; attempt++) {
            byte[] lookupBytes = randomBytes(LOOKUP_BYTES);
            byte[] secret = randomBytes(OpaqueTokenDigestPort.TOKEN_SECRET_BYTES);
            String lookupId = encode(lookupBytes);
            String completeToken = TOKEN_PREFIX + lookupId + "." + encode(secret);
            try {
                OpaqueTokenDigestPort.Binding binding = binding(request.tenant(), request.subject(),
                        request.protectedObjectId(), request.purpose());
                VersionedTokenDigest credentialDigest = tokenDigestPort.issue(
                        OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, binding, secret);
                StoredCapability stored = new StoredCapability(
                        lookupId,
                        request.protectedObjectId(),
                        digestBinding("tenant", request.tenant()),
                        digestBinding("subject", request.subject()),
                        request.purpose(),
                        credentialDigest,
                        ObjectAccessAuthorizationPort.CapabilityState.ACTIVE,
                        request.expiresAt());
                if (capabilityStore.create(stored)) {
                    return new ObjectCapabilityToken(CAPABILITY_PATH_PREFIX + completeToken);
                }
            } catch (RuntimeException failure) {
                throw Failure.unavailable();
            } finally {
                Arrays.fill(lookupBytes, (byte) 0);
                Arrays.fill(secret, (byte) 0);
            }
        }
        throw Failure.unavailable();
    }

    /**
     * Atomically consumes the capability after validation and authorization, then executes one
     * downstream fetch. A downstream failure burns the capability; callers must obtain a new one.
     */
    public <T> T authorizeAndFetch(String completeToken,
                                   AccessRequest request,
                                   Supplier<T> downstreamFetch) {
        Objects.requireNonNull(downstreamFetch, "downstreamFetch");
        ParsedToken parsed = parse(completeToken).orElseThrow(Failure::denied);
        byte[] secret = parsed.secret();
        try {
            StoredCapability stored = capabilityStore.findByLookupId(parsed.lookupId())
                    .orElseThrow(Failure::denied);
            if (!validStoredCapability(stored, request, secret)) {
                throw Failure.denied();
            }
            ObjectAccessAuthorizationPort.Request authorizationRequest =
                    new ObjectAccessAuthorizationPort.Request(
                            stored.protectedObjectId(), request.tenant(), request.subject(),
                            stored.purpose(), stored.state(), stored.expiresAt());
            if (!authorized(authorizationRequest)) {
                throw Failure.denied();
            }
            if (!capabilityStore.consumeActive(parsed.lookupId(), clock.instant())) {
                throw Failure.denied();
            }
            return downstreamFetch.get();
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private boolean validStoredCapability(StoredCapability stored,
                                           AccessRequest request,
                                           byte[] secret) {
        if (stored.state() != ObjectAccessAuthorizationPort.CapabilityState.ACTIVE
                || !clock.instant().isBefore(stored.expiresAt())
                || !stored.protectedObjectId().equals(request.protectedObjectId())
                || !stored.purpose().equals(request.purpose())
                || !MessageDigest.isEqual(stored.tenantBindingDigest(),
                        digestBinding("tenant", request.tenant()))
                || !MessageDigest.isEqual(stored.subjectBindingDigest(),
                        digestBinding("subject", request.subject()))) {
            return false;
        }
        try {
            return tokenDigestPort.verify(
                    OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                    binding(request.tenant(), request.subject(), request.protectedObjectId(), request.purpose()),
                    secret,
                    stored.credentialDigest());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private boolean authorized(ObjectAccessAuthorizationPort.Request request) {
        try {
            return authorizationPort.authorize(request);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private void requireIssueRequest(IssueRequest request) {
        if (request == null || !clock.instant().isBefore(request.expiresAt())) {
            throw Failure.invalidInput();
        }
    }

    private byte[] randomBytes(int size) {
        byte[] value = new byte[size];
        try {
            secureRandom.nextBytes(value);
            return value;
        } catch (ProviderException failure) {
            Arrays.fill(value, (byte) 0);
            throw Failure.unavailable();
        }
    }

    private static Optional<ParsedToken> parse(String completeToken) {
        if (completeToken == null || !completeToken.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        String encoded = completeToken.substring(TOKEN_PREFIX.length());
        int separator = encoded.indexOf('.');
        if (separator != LOOKUP_CHARACTERS || encoded.indexOf('.', separator + 1) >= 0) {
            return Optional.empty();
        }
        String lookupId = encoded.substring(0, separator);
        String secretValue = encoded.substring(separator + 1);
        if (!LOOKUP.matcher(lookupId).matches() || !SECRET.matcher(secretValue).matches()) {
            return Optional.empty();
        }
        try {
            byte[] secret = Base64.getUrlDecoder().decode(secretValue);
            if (secret.length != OpaqueTokenDigestPort.TOKEN_SECRET_BYTES
                    || !encode(secret).equals(secretValue)) {
                Arrays.fill(secret, (byte) 0);
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(lookupId, secret));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private static OpaqueTokenDigestPort.Binding binding(String tenant,
                                                          String subject,
                                                          String protectedObjectId,
                                                          String purpose) {
        return new OpaqueTokenDigestPort.Binding(
                tenant,
                subject,
                "object:" + protectedObjectId + "/purpose:" + purpose);
    }

    private static byte[] digestBinding(String type, String value) {
        try {
            byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
            byte[] valueBytes = value.getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream canonical = new ByteArrayOutputStream();
            canonical.writeBytes(BINDING_DIGEST_DOMAIN);
            writeLengthPrefixed(canonical, typeBytes);
            writeLengthPrefixed(canonical, valueBytes);
            return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray());
        } catch (NoSuchAlgorithmException | ProviderException failure) {
            throw Failure.unavailable();
        }
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream output, byte[] value) {
        output.writeBytes(ByteBuffer.allocate(2).putShort((short) value.length).array());
        output.writeBytes(value);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void requireComponent(String value) {
        if (value == null || !COMPONENT.matcher(value).matches()) {
            throw Failure.invalidInput();
        }
    }

    private static void requirePurpose(String value) {
        if (value == null || !PURPOSE.matcher(value).matches()) {
            throw Failure.invalidInput();
        }
    }

    private static void requireCanonicalBinding(String tenant,
                                                String subject,
                                                String protectedObjectId,
                                                String purpose) {
        try {
            binding(tenant, subject, protectedObjectId, purpose);
        } catch (IllegalArgumentException failure) {
            throw Failure.invalidInput();
        }
    }

    public record IssueRequest(String protectedObjectId,
                               String tenant,
                               String subject,
                               String purpose,
                               Instant expiresAt) {
        public IssueRequest {
            requireComponent(protectedObjectId);
            requireComponent(tenant);
            requireComponent(subject);
            requirePurpose(purpose);
            if (expiresAt == null) {
                throw Failure.invalidInput();
            }
            requireCanonicalBinding(tenant, subject, protectedObjectId, purpose);
        }
    }

    public record AccessRequest(String protectedObjectId,
                                String tenant,
                                String subject,
                                String purpose) {
        public AccessRequest {
            requireComponent(protectedObjectId);
            requireComponent(tenant);
            requireComponent(subject);
            requirePurpose(purpose);
            requireCanonicalBinding(tenant, subject, protectedObjectId, purpose);
        }
    }

    /** Persistence seam whose values cannot contain a token or token secret. */
    public interface CapabilityStore {
        boolean create(StoredCapability capability);

        Optional<StoredCapability> findByLookupId(String lookupId);

        /** ACTIVE-to-terminal CAS; exactly one concurrent caller may succeed. */
        boolean consumeActive(String lookupId, Instant now);
    }

    /** Immutable safe storage representation for ycs_crypto_object_capabilities. */
    public static final class StoredCapability {
        private final String lookupId;
        private final String protectedObjectId;
        private final byte[] tenantBindingDigest;
        private final byte[] subjectBindingDigest;
        private final String purpose;
        private final VersionedTokenDigest credentialDigest;
        private final ObjectAccessAuthorizationPort.CapabilityState state;
        private final Instant expiresAt;

        public StoredCapability(String lookupId,
                                String protectedObjectId,
                                byte[] tenantBindingDigest,
                                byte[] subjectBindingDigest,
                                String purpose,
                                VersionedTokenDigest credentialDigest,
                                ObjectAccessAuthorizationPort.CapabilityState state,
                                Instant expiresAt) {
            if (lookupId == null || !LOOKUP.matcher(lookupId).matches()
                    || tenantBindingDigest == null || tenantBindingDigest.length != 32
                    || subjectBindingDigest == null || subjectBindingDigest.length != 32
                    || credentialDigest == null || state == null || expiresAt == null) {
                throw Failure.invalidInput();
            }
            requireComponent(protectedObjectId);
            requirePurpose(purpose);
            this.lookupId = lookupId;
            this.protectedObjectId = protectedObjectId;
            this.tenantBindingDigest = tenantBindingDigest.clone();
            this.subjectBindingDigest = subjectBindingDigest.clone();
            this.purpose = purpose;
            this.credentialDigest = credentialDigest;
            if (credentialDigest.purpose() != OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY) {
                throw Failure.invalidInput();
            }
            this.state = state;
            this.expiresAt = expiresAt;
        }

        public String lookupId() {
            return lookupId;
        }

        public String protectedObjectId() {
            return protectedObjectId;
        }

        public byte[] tenantBindingDigest() {
            return tenantBindingDigest.clone();
        }

        public byte[] subjectBindingDigest() {
            return subjectBindingDigest.clone();
        }

        public String purpose() {
            return purpose;
        }

        public VersionedTokenDigest credentialDigest() {
            return credentialDigest;
        }

        public ObjectAccessAuthorizationPort.CapabilityState state() {
            return state;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public StoredCapability withState(ObjectAccessAuthorizationPort.CapabilityState newState) {
            return new StoredCapability(lookupId, protectedObjectId, tenantBindingDigest,
                    subjectBindingDigest, purpose, credentialDigest, newState, expiresAt);
        }

        @Override
        public String toString() {
            return "StoredCapability[lookupId=" + lookupId + ", protectedObjectId="
                    + protectedObjectId + ", bindings=[redacted], purpose=" + purpose
                    + ", credentialDigest=" + credentialDigest + ", state=" + state
                    + ", expiresAt=" + expiresAt + "]";
        }
    }

    /** Stable access failure with no token, provider detail or binding oracle. */
    public static final class Failure extends RuntimeException {
        public enum Category {
            CAPABILITY_INPUT_INVALID,
            CAPABILITY_DENIED,
            CAPABILITY_UNAVAILABLE
        }

        private final Category category;

        private Failure(Category category, String message) {
            super(message, null, false, false);
            this.category = category;
        }

        public Category category() {
            return category;
        }

        static Failure invalidInput() {
            return new Failure(Category.CAPABILITY_INPUT_INVALID, "object capability input is invalid");
        }

        static Failure denied() {
            return new Failure(Category.CAPABILITY_DENIED, "object capability access denied");
        }

        static Failure unavailable() {
            return new Failure(Category.CAPABILITY_UNAVAILABLE, "object capability is unavailable");
        }
    }

    private record ParsedToken(String lookupId, byte[] secret) {
    }
}
