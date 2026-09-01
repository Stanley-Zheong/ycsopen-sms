package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Sole owner of the tenant-registration upload credential and OPEN-session admission rules.
 *
 * <p>The complete credential is returned once and is never stored. Media, size, and magic-byte
 * checks precede the atomic reservation. Once admitted, every outcome consumes the reserved slot.
 * Session state and per-purpose/session counters are serialized by the persistence boundary.</p>
 */
public final class TenantRegistrationObjectSessionService {

    public static final Duration SESSION_TTL = Duration.ofHours(24);
    public static final int MAX_ATTEMPTS_PER_PURPOSE = 3;
    public static final int MAX_ATTEMPTS_PER_SESSION = 15;
    public static final String TOKEN_PREFIX = "regup_v1_";
    public static final String UPLOAD_TOKEN_HEADER = "X-Registration-Upload-Token";

    private static final int TOKEN_LOOKUP_BYTES = 16;
    private static final int TOKEN_SECRET_BYTES = OpaqueTokenDigestPort.TOKEN_SECRET_BYTES;
    private static final int TOKEN_LOOKUP_CHARACTERS = 22;
    private static final int TOKEN_SECRET_CHARACTERS = 43;
    private static final int ISSUE_ATTEMPTS = 4;
    private static final int SIGNATURE_BYTES = 8;
    private static final String TOKEN_SUBJECT = "registration-upload";
    private static final Pattern LOOKUP = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    private final OpaqueTokenDigestPort tokenDigestPort;
    private final ProtectedObjectService protectedObjectService;
    private final SessionStore sessionStore;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public TenantRegistrationObjectSessionService(OpaqueTokenDigestPort tokenDigestPort,
                                                   ProtectedObjectService protectedObjectService,
                                                   SessionStore sessionStore,
                                                   Clock clock,
                                                   SecureRandom secureRandom) {
        this.tokenDigestPort = Objects.requireNonNull(tokenDigestPort, "tokenDigestPort");
        this.protectedObjectService = Objects.requireNonNull(
                protectedObjectService, "protectedObjectService");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    /** Production persistence constructor; object-store composition remains configuration-owned. */
    public TenantRegistrationObjectSessionService(OpaqueTokenDigestPort tokenDigestPort,
                                                   ProtectedObjectService protectedObjectService,
                                                   JdbcTemplate jdbcTemplate,
                                                   PlatformTransactionManager transactionManager,
                                                   Clock clock,
                                                   SecureRandom secureRandom) {
        this(tokenDigestPort, protectedObjectService,
                new JdbcSessionStore(jdbcTemplate, transactionManager), clock, secureRandom);
    }

    /** Issues one repeat-use credential while retaining only its keyed, versioned digest. */
    public CreatedSession createSession() {
        for (int attempt = 0; attempt < ISSUE_ATTEMPTS; attempt++) {
            byte[] sessionBytes = randomBytes(TOKEN_LOOKUP_BYTES);
            byte[] tenantDraftBytes = randomBytes(TOKEN_LOOKUP_BYTES);
            byte[] secret = randomBytes(TOKEN_SECRET_BYTES);
            try {
                String sessionId = uuid(sessionBytes);
                String tenantDraftId = uuid(tenantDraftBytes);
                String lookupId = encode(sessionBytes);
                Instant expiresAt = clock.instant().plus(SESSION_TTL);
                VersionedTokenDigest digest = tokenDigestPort.issue(
                        OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                        binding(tenantDraftId, sessionId), secret);
                StoredSession stored = new StoredSession(sessionId, tenantDraftId,
                        SessionState.OPEN, digest, expiresAt, 0);
                if (sessionStore.create(stored)) {
                    return new CreatedSession(sessionId,
                            TOKEN_PREFIX + lookupId + "." + encode(secret), expiresAt);
                }
            } catch (Failure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw Failure.unavailable();
            } finally {
                clear(sessionBytes);
                clear(tenantDraftBytes);
                clear(secret);
            }
        }
        throw Failure.unavailable();
    }

    /** Validates before admission, atomically burns one slot, then creates encrypted storage. */
    public UploadedObject upload(UploadRequest request) {
        requireUploadRequest(request);
        BufferedInputStream boundedInput = buffered(request.input());
        validateBeforeReservation(request, boundedInput);

        ParsedToken parsed = parse(request.uploadToken()).orElseThrow(Failure::tokenInvalid);
        byte[] secret = parsed.secret();
        try {
            if (!lookupForSession(request.registrationSessionId()).equals(parsed.lookupId())) {
                throw Failure.tokenInvalid();
            }
            Instant now = clock.instant();
            Reservation reservation = sessionStore.reserve(
                    request.registrationSessionId(), request.purpose(), now,
                    stored -> verify(stored, secret));
            ProtectedObjectService.CreatedObject created;
            try {
                created = protectedObjectService.create(new ProtectedObjectService.CreateRequest(
                        reservation.registrationSessionId(), reservation.tenantDraftId(),
                        request.purpose().objectPurpose(), request.mediaType(), boundedInput,
                        request.declaredPlaintextLength(), reservation.attemptNumber(),
                        reservation.expiresAt(), reservation.replacesObjectId()));
            } catch (ProtectedObjectService.Failure failure) {
                throw mapObjectFailure(failure);
            } catch (RuntimeException failure) {
                throw Failure.unavailable();
            }
            return new UploadedObject(created.protectedObjectId(), request.purpose(),
                    reservation.expiresAt());
        } finally {
            clear(secret);
        }
    }

    /** Explicit terminal close; remaining STAGED rows become reconciliation candidates. */
    public SessionState close(String registrationSessionId, String uploadToken) {
        return transition(registrationSessionId, uploadToken, SessionState.CLOSED);
    }

    /** Terminal transition used by the registration transaction owner after a successful claim. */
    public SessionState claim(String registrationSessionId, String uploadToken) {
        return transition(registrationSessionId, uploadToken, SessionState.CLAIMED);
    }

    private SessionState transition(String registrationSessionId,
                                    String uploadToken,
                                    SessionState targetState) {
        requireSessionId(registrationSessionId);
        ParsedToken parsed = parse(uploadToken).orElseThrow(Failure::tokenInvalid);
        byte[] secret = parsed.secret();
        try {
            if (!lookupForSession(registrationSessionId).equals(parsed.lookupId())) {
                throw Failure.tokenInvalid();
            }
            return sessionStore.transition(registrationSessionId, targetState, clock.instant(),
                    stored -> verify(stored, secret));
        } finally {
            clear(secret);
        }
    }

    private boolean verify(StoredSession stored, byte[] secret) {
        if (stored.state() != SessionState.OPEN || !clock.instant().isBefore(stored.expiresAt())
                || stored.credentialDigest().purpose()
                != OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD) {
            return false;
        }
        try {
            return tokenDigestPort.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                    binding(stored.tenantDraftId(), stored.registrationSessionId()),
                    secret, stored.credentialDigest());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static void validateBeforeReservation(UploadRequest request,
                                                  BufferedInputStream input) {
        long maximum = request.purpose().maximumPlaintextBytes();
        if (request.declaredPlaintextLength() < 1
                || request.declaredPlaintextLength() > maximum) {
            throw Failure.sizeLimitExceeded();
        }
        if (!request.purpose().accepts(request.mediaType())) {
            throw Failure.mediaTypeNotAccepted();
        }
        byte[] prefix = new byte[SIGNATURE_BYTES];
        int count = 0;
        try {
            input.mark(SIGNATURE_BYTES);
            while (count < prefix.length) {
                int read = input.read(prefix, count, prefix.length - count);
                if (read == -1) {
                    break;
                }
                if (read == 0) {
                    int one = input.read();
                    if (one == -1) {
                        break;
                    }
                    prefix[count++] = (byte) one;
                } else {
                    count += read;
                }
            }
            input.reset();
        } catch (IOException failure) {
            throw Failure.inputInvalid();
        }
        if (!matchesSignature(request.mediaType(), prefix, count)) {
            throw Failure.signatureMismatch();
        }
    }

    private static boolean matchesSignature(String mediaType, byte[] prefix, int count) {
        return switch (mediaType) {
            case "application/pdf" -> count >= 5
                    && prefix[0] == '%' && prefix[1] == 'P' && prefix[2] == 'D'
                    && prefix[3] == 'F' && prefix[4] == '-';
            case "image/jpeg" -> count >= 3
                    && prefix[0] == (byte) 0xff && prefix[1] == (byte) 0xd8
                    && prefix[2] == (byte) 0xff;
            case "image/png" -> count >= 8
                    && Arrays.equals(prefix, new byte[]{(byte) 0x89, 'P', 'N', 'G',
                    0x0d, 0x0a, 0x1a, 0x0a});
            default -> false;
        };
    }

    private static BufferedInputStream buffered(InputStream input) {
        if (input instanceof BufferedInputStream buffered) {
            return buffered;
        }
        return new BufferedInputStream(input, SIGNATURE_BYTES);
    }

    private static void requireUploadRequest(UploadRequest request) {
        if (request == null || request.purpose() == null || request.input() == null
                || request.mediaType() == null || request.uploadToken() == null) {
            throw Failure.inputInvalid();
        }
        requireSessionId(request.registrationSessionId());
    }

    private static Optional<ParsedToken> parse(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        String encoded = token.substring(TOKEN_PREFIX.length());
        int separator = encoded.indexOf('.');
        if (separator != TOKEN_LOOKUP_CHARACTERS
                || encoded.indexOf('.', separator + 1) >= 0) {
            return Optional.empty();
        }
        String lookup = encoded.substring(0, separator);
        String encodedSecret = encoded.substring(separator + 1);
        if (!LOOKUP.matcher(lookup).matches() || !SECRET.matcher(encodedSecret).matches()) {
            return Optional.empty();
        }
        try {
            byte[] secret = Base64.getUrlDecoder().decode(encodedSecret);
            if (secret.length != TOKEN_SECRET_BYTES || !encode(secret).equals(encodedSecret)) {
                clear(secret);
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(lookup, secret));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private static OpaqueTokenDigestPort.Binding binding(String tenantDraftId,
                                                          String registrationSessionId) {
        return new OpaqueTokenDigestPort.Binding("tenant-draft:" + tenantDraftId,
                TOKEN_SUBJECT, "session:" + registrationSessionId);
    }

    private static String lookupForSession(String registrationSessionId) {
        UUID uuid = UUID.fromString(registrationSessionId);
        byte[] bytes = new byte[TOKEN_LOOKUP_BYTES];
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        for (int index = 7; index >= 0; index--) {
            bytes[index] = (byte) high;
            high >>>= 8;
            bytes[index + 8] = (byte) low;
            low >>>= 8;
        }
        try {
            return encode(bytes);
        } finally {
            clear(bytes);
        }
    }

    private byte[] randomBytes(int count) {
        byte[] value = new byte[count];
        try {
            secureRandom.nextBytes(value);
            return value;
        } catch (ProviderException failure) {
            clear(value);
            throw Failure.unavailable();
        }
    }

    private static String uuid(byte[] bytes) {
        byte[] canonical = bytes.clone();
        try {
            canonical[6] = (byte) ((canonical[6] & 0x0f) | 0x40);
            canonical[8] = (byte) ((canonical[8] & 0x3f) | 0x80);
            long high = 0;
            long low = 0;
            for (int index = 0; index < 8; index++) {
                high = high << 8 | canonical[index] & 0xffL;
                low = low << 8 | canonical[index + 8] & 0xffL;
            }
            System.arraycopy(canonical, 0, bytes, 0, canonical.length);
            return new UUID(high, low).toString();
        } finally {
            clear(canonical);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void requireSessionId(String value) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            throw Failure.inputInvalid();
        }
    }

    private static Failure mapObjectFailure(ProtectedObjectService.Failure failure) {
        return switch (failure.category()) {
            case PROTECTED_OBJECT_INPUT_INVALID -> Failure.inputInvalid();
            case PROTECTED_OBJECT_ACCESS_DENIED -> Failure.tokenInvalid();
            case PROTECTED_OBJECT_INTEGRITY_INVALID, PROTECTED_OBJECT_UNAVAILABLE ->
                    Failure.unavailable();
        };
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    public enum UploadPurpose {
        BUSINESS_LICENSE("business-license", PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE,
                10_485_760L, List.of("application/pdf", "image/jpeg", "image/png")),
        LEGAL_REP_ID_FRONT("legal-rep-id-front",
                PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_FRONT,
                5_242_880L, List.of("image/jpeg", "image/png")),
        LEGAL_REP_ID_BACK("legal-rep-id-back",
                PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_BACK,
                5_242_880L, List.of("image/jpeg", "image/png")),
        SHORTLINK_DOMAIN_PROOF("shortlink-domain-proof",
                PrivateObjectStorePort.ObjectPurpose.SHORT_LINK_DOMAIN_PROOF,
                10_485_760L, List.of("application/pdf", "image/jpeg", "image/png")),
        TRADEMARK_PROOF("trademark-proof", PrivateObjectStorePort.ObjectPurpose.TRADEMARK_PROOF,
                10_485_760L, List.of("application/pdf", "image/jpeg", "image/png"));

        private final String wireName;
        private final PrivateObjectStorePort.ObjectPurpose objectPurpose;
        private final long maximumPlaintextBytes;
        private final List<String> mediaTypes;

        UploadPurpose(String wireName,
                      PrivateObjectStorePort.ObjectPurpose objectPurpose,
                      long maximumPlaintextBytes,
                      List<String> mediaTypes) {
            this.wireName = wireName;
            this.objectPurpose = objectPurpose;
            this.maximumPlaintextBytes = maximumPlaintextBytes;
            this.mediaTypes = mediaTypes;
            if (maximumPlaintextBytes != objectPurpose.envelopeTarget().maximumPlaintextBytes()) {
                throw new IllegalArgumentException("registration upload limit is inconsistent");
            }
        }

        @JsonValue
        public String wireName() {
            return wireName;
        }

        public PrivateObjectStorePort.ObjectPurpose objectPurpose() {
            return objectPurpose;
        }

        public long maximumPlaintextBytes() {
            return maximumPlaintextBytes;
        }

        public long maximumEnvelopeBytes() {
            return objectPurpose.maximumEnvelopeBytes();
        }

        public List<String> mediaTypes() {
            return mediaTypes;
        }

        boolean accepts(String mediaType) {
            return mediaTypes.contains(mediaType);
        }

        public static Optional<UploadPurpose> parse(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    public enum SessionState {
        OPEN,
        CLAIMED,
        CLOSED,
        EXPIRED
    }

    public record CreatedSession(String registrationObjectSessionId,
                                 String registrationUploadToken,
                                 Instant expiresAt) {
        @Override
        public String toString() {
            return "CreatedSession[registrationObjectSessionId=" + registrationObjectSessionId
                    + ", registrationUploadToken=[redacted], expiresAt=" + expiresAt + "]";
        }
    }

    public record UploadRequest(String registrationSessionId,
                                String uploadToken,
                                UploadPurpose purpose,
                                String mediaType,
                                InputStream input,
                                long declaredPlaintextLength) {
        @Override
        public String toString() {
            return "UploadRequest[registrationSessionId=[redacted], uploadToken=[redacted], purpose="
                    + purpose + ", mediaType=" + mediaType + ", input=[redacted], length="
                    + declaredPlaintextLength + "]";
        }
    }

    public record UploadedObject(String protectedObjectId,
                                 UploadPurpose purpose,
                                 Instant expiresAt) {
    }

    /** Safe persistence value; it contains no complete token, token secret, or store locator. */
    public static final class StoredSession {
        private final String registrationSessionId;
        private final String tenantDraftId;
        private final SessionState state;
        private final VersionedTokenDigest credentialDigest;
        private final Instant expiresAt;
        private final int admittedAttemptCount;

        public StoredSession(String registrationSessionId,
                             String tenantDraftId,
                             SessionState state,
                             VersionedTokenDigest credentialDigest,
                             Instant expiresAt,
                             int admittedAttemptCount) {
            requireSessionId(registrationSessionId);
            requireSessionId(tenantDraftId);
            if (state == null || credentialDigest == null || expiresAt == null
                    || admittedAttemptCount < 0
                    || admittedAttemptCount > MAX_ATTEMPTS_PER_SESSION
                    || credentialDigest.purpose()
                    != OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD) {
                throw new IllegalArgumentException("registration session is invalid");
            }
            this.registrationSessionId = registrationSessionId;
            this.tenantDraftId = tenantDraftId;
            this.state = state;
            this.credentialDigest = credentialDigest;
            this.expiresAt = expiresAt;
            this.admittedAttemptCount = admittedAttemptCount;
        }

        public String registrationSessionId() {
            return registrationSessionId;
        }

        public String tenantDraftId() {
            return tenantDraftId;
        }

        public SessionState state() {
            return state;
        }

        public VersionedTokenDigest credentialDigest() {
            return credentialDigest;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public int admittedAttemptCount() {
            return admittedAttemptCount;
        }

        @Override
        public String toString() {
            return "StoredSession[binding=[redacted], state=" + state
                    + ", credentialDigest=" + credentialDigest + ", expiresAt=" + expiresAt
                    + ", admittedAttemptCount=" + admittedAttemptCount + "]";
        }
    }

    public record Reservation(String registrationSessionId,
                              String tenantDraftId,
                              UploadPurpose purpose,
                              int attemptNumber,
                              int sessionAttemptNumber,
                              Instant expiresAt,
                              String replacesObjectId) {
    }

    public interface SessionStore {
        boolean create(StoredSession session);

        Reservation reserve(String registrationSessionId,
                            UploadPurpose purpose,
                            Instant now,
                            Predicate<StoredSession> credentialVerifier);

        SessionState transition(String registrationSessionId,
                                SessionState targetState,
                                Instant now,
                                Predicate<StoredSession> credentialVerifier);
    }

    /** JDBC implementation with a session-row lock serializing both admission ceilings. */
    public static final class JdbcSessionStore implements SessionStore {
        private static final String DIGEST_PURPOSE = "REGISTRATION_UPLOAD_DIGEST";

        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;

        public JdbcSessionStore(JdbcTemplate jdbc,
                                PlatformTransactionManager transactionManager) {
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.transactions = new TransactionTemplate(
                    Objects.requireNonNull(transactionManager, "transactionManager"));
        }

        @Override
        public boolean create(StoredSession session) {
            try {
                return jdbc.update("""
                        INSERT INTO ycs_crypto_registration_sessions
                            (registration_session_id, tenant_draft_id, session_state,
                             upload_digest_purpose, upload_digest_key_version,
                             upload_credential_digest, admitted_attempt_count, expires_at)
                        VALUES (?, ?, 'OPEN', ?, ?, ?, 0, ?)
                        """, session.registrationSessionId(), session.tenantDraftId(),
                        DIGEST_PURPOSE, session.credentialDigest().keyVersion(),
                        session.credentialDigest().digest(), Timestamp.from(session.expiresAt())) == 1;
            } catch (DuplicateKeyException collision) {
                return false;
            } catch (RuntimeException failure) {
                throw Failure.unavailable();
            }
        }

        @Override
        public Reservation reserve(String registrationSessionId,
                                   UploadPurpose purpose,
                                   Instant now,
                                   Predicate<StoredSession> credentialVerifier) {
            try {
                OperationResult<Reservation> outcome = transactions.execute(status -> {
                    Optional<StoredSession> found = lockSession(registrationSessionId);
                    if (found.isEmpty()) {
                        return OperationResult.failure(Failure.tokenInvalid());
                    }
                    StoredSession session = found.orElseThrow();
                    Failure invalid = validateOpenAndVerified(session, now, credentialVerifier);
                    if (invalid != null) {
                        return OperationResult.failure(invalid);
                    }
                    int purposeAttempts = lockPurposeAttempts(registrationSessionId, purpose);
                    if (session.admittedAttemptCount() >= MAX_ATTEMPTS_PER_SESSION
                            || purposeAttempts >= MAX_ATTEMPTS_PER_PURPOSE) {
                        return OperationResult.failure(Failure.limitReached());
                    }
                    int nextPurpose = Math.addExact(purposeAttempts, 1);
                    int nextSession = Math.addExact(session.admittedAttemptCount(), 1);
                    int purposeChanged = jdbc.update("""
                            UPDATE ycs_crypto_registration_upload_attempts
                               SET admitted_attempt_count = ?, optimistic_version = optimistic_version + 1
                             WHERE registration_session_id = ? AND object_purpose = ?
                            """, nextPurpose, registrationSessionId, databasePurpose(purpose));
                    int sessionChanged = jdbc.update("""
                            UPDATE ycs_crypto_registration_sessions
                               SET admitted_attempt_count = ?, optimistic_version = optimistic_version + 1
                             WHERE registration_session_id = ? AND session_state = 'OPEN'
                            """, nextSession, registrationSessionId);
                    if (purposeChanged != 1 || sessionChanged != 1) {
                        throw Failure.unavailable();
                    }
                    return OperationResult.success(new Reservation(
                            registrationSessionId, session.tenantDraftId(), purpose,
                            nextPurpose, nextSession, session.expiresAt(),
                            currentObject(registrationSessionId, purpose).orElse(null)));
                });
                OperationResult<Reservation> result = Objects.requireNonNull(outcome, "reservation");
                if (result.failure() != null) {
                    throw result.failure();
                }
                return Objects.requireNonNull(result.value(), "reservation value");
            } catch (Failure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw Failure.unavailable();
            }
        }

        @Override
        public SessionState transition(String registrationSessionId,
                                       SessionState targetState,
                                       Instant now,
                                       Predicate<StoredSession> credentialVerifier) {
            if (targetState != SessionState.CLOSED && targetState != SessionState.CLAIMED) {
                throw new IllegalArgumentException("terminal session state is invalid");
            }
            try {
                OperationResult<SessionState> outcome = transactions.execute(status -> {
                    Optional<StoredSession> found = lockSession(registrationSessionId);
                    if (found.isEmpty()) {
                        return OperationResult.failure(Failure.tokenInvalid());
                    }
                    StoredSession session = found.orElseThrow();
                    Failure invalid = validateOpenAndVerified(session, now, credentialVerifier);
                    if (invalid != null) {
                        return OperationResult.failure(invalid);
                    }
                    int changed = jdbc.update("""
                            UPDATE ycs_crypto_registration_sessions
                               SET session_state = ?, optimistic_version = optimistic_version + 1
                             WHERE registration_session_id = ? AND session_state = 'OPEN'
                            """, targetState.name(), registrationSessionId);
                    if (changed != 1) {
                        throw Failure.sessionNotOpen();
                    }
                    if (targetState == SessionState.CLOSED) {
                        jdbc.update("""
                                UPDATE ycs_crypto_protected_objects
                                   SET object_state = 'EXPIRED', optimistic_version = optimistic_version + 1
                                 WHERE registration_session_id = ? AND object_state = 'STAGED'
                                """, registrationSessionId);
                    }
                    return OperationResult.success(targetState);
                });
                OperationResult<SessionState> result = Objects.requireNonNull(outcome, "session state");
                if (result.failure() != null) {
                    throw result.failure();
                }
                return Objects.requireNonNull(result.value(), "session state value");
            } catch (Failure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw Failure.unavailable();
            }
        }

        private Optional<StoredSession> lockSession(String registrationSessionId) {
            List<StoredSession> rows = jdbc.query("""
                    SELECT registration_session_id, tenant_draft_id, session_state,
                           upload_digest_key_version, upload_credential_digest,
                           expires_at, admitted_attempt_count
                      FROM ycs_crypto_registration_sessions
                     WHERE registration_session_id = ?
                       AND upload_digest_purpose = ?
                     FOR UPDATE
                    """, (rs, row) -> new StoredSession(
                    rs.getString("registration_session_id"), rs.getString("tenant_draft_id"),
                    SessionState.valueOf(rs.getString("session_state")),
                    new VersionedTokenDigest(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                            rs.getLong("upload_digest_key_version"),
                            rs.getBytes("upload_credential_digest")),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getInt("admitted_attempt_count")), registrationSessionId, DIGEST_PURPOSE);
            return rows.stream().findFirst();
        }

        private int lockPurposeAttempts(String registrationSessionId, UploadPurpose purpose) {
            String databasePurpose = databasePurpose(purpose);
            jdbc.update("""
                    INSERT INTO ycs_crypto_registration_upload_attempts
                        (registration_session_id, object_purpose, admitted_attempt_count)
                    VALUES (?, ?, 0)
                    ON DUPLICATE KEY UPDATE registration_session_id = VALUES(registration_session_id)
                    """, registrationSessionId, databasePurpose);
            return jdbc.queryForObject("""
                    SELECT admitted_attempt_count
                      FROM ycs_crypto_registration_upload_attempts
                     WHERE registration_session_id = ? AND object_purpose = ?
                     FOR UPDATE
                    """, Integer.class, registrationSessionId, databasePurpose);
        }

        private Optional<String> currentObject(String registrationSessionId, UploadPurpose purpose) {
            return jdbc.query("""
                    SELECT protected_object_id
                      FROM ycs_crypto_protected_objects
                     WHERE registration_session_id = ? AND object_purpose = ?
                       AND object_state = 'STAGED'
                    """, (rs, row) -> rs.getString(1), registrationSessionId,
                    databasePurpose(purpose)).stream().findFirst();
        }

        private Failure validateOpenAndVerified(StoredSession session,
                                                Instant now,
                                                Predicate<StoredSession> credentialVerifier) {
            if (!now.isBefore(session.expiresAt())) {
                jdbc.update("""
                        UPDATE ycs_crypto_registration_sessions
                           SET session_state = 'EXPIRED', optimistic_version = optimistic_version + 1
                         WHERE registration_session_id = ? AND session_state = 'OPEN'
                        """, session.registrationSessionId());
                jdbc.update("""
                        UPDATE ycs_crypto_protected_objects
                           SET object_state = 'EXPIRED', optimistic_version = optimistic_version + 1
                         WHERE registration_session_id = ? AND object_state = 'STAGED'
                        """, session.registrationSessionId());
                return Failure.sessionExpired();
            }
            if (session.state() != SessionState.OPEN) {
                return Failure.sessionNotOpen();
            }
            if (!credentialVerifier.test(session)) {
                return Failure.tokenInvalid();
            }
            return null;
        }

        private static String databasePurpose(UploadPurpose purpose) {
            return switch (purpose) {
                case BUSINESS_LICENSE -> "BUSINESS_LICENSE";
                case LEGAL_REP_ID_FRONT -> "LEGAL_REPRESENTATIVE_ID_FRONT";
                case LEGAL_REP_ID_BACK -> "LEGAL_REPRESENTATIVE_ID_BACK";
                case SHORTLINK_DOMAIN_PROOF -> "SHORT_LINK_PROOF";
                case TRADEMARK_PROOF -> "TRADEMARK_PROOF";
            };
        }

        private record OperationResult<T>(T value, Failure failure) {
            private static <T> OperationResult<T> success(T value) {
                return new OperationResult<>(Objects.requireNonNull(value, "value"), null);
            }

            private static <T> OperationResult<T> failure(Failure failure) {
                return new OperationResult<>(null, Objects.requireNonNull(failure, "failure"));
            }
        }
    }

    private record ParsedToken(String lookupId, byte[] secret) {
    }

    /** Stable cause-free boundary with no token, binding, provider, or storage detail. */
    public static final class Failure extends RuntimeException {
        public enum Category {
            REGISTRATION_UPLOAD_INPUT_INVALID,
            REGISTRATION_UPLOAD_TOKEN_INVALID,
            REGISTRATION_UPLOAD_SESSION_NOT_OPEN,
            REGISTRATION_UPLOAD_SESSION_EXPIRED,
            REGISTRATION_UPLOAD_MEDIA_TYPE_NOT_ACCEPTED,
            REGISTRATION_UPLOAD_SIZE_LIMIT_EXCEEDED,
            REGISTRATION_UPLOAD_SIGNATURE_MISMATCH,
            REGISTRATION_UPLOAD_LIMIT_REACHED,
            REGISTRATION_UPLOAD_UNAVAILABLE
        }

        private final Category category;

        private Failure(Category category, String message) {
            super(message, null, false, false);
            this.category = category;
        }

        public Category category() {
            return category;
        }

        public static Failure inputInvalid() {
            return new Failure(Category.REGISTRATION_UPLOAD_INPUT_INVALID,
                    "registration upload input is invalid");
        }

        public static Failure tokenInvalid() {
            return new Failure(Category.REGISTRATION_UPLOAD_TOKEN_INVALID,
                    "registration upload credential is invalid");
        }

        public static Failure sessionNotOpen() {
            return new Failure(Category.REGISTRATION_UPLOAD_SESSION_NOT_OPEN,
                    "registration upload session is not open");
        }

        public static Failure sessionExpired() {
            return new Failure(Category.REGISTRATION_UPLOAD_SESSION_EXPIRED,
                    "registration upload session has expired");
        }

        public static Failure mediaTypeNotAccepted() {
            return new Failure(Category.REGISTRATION_UPLOAD_MEDIA_TYPE_NOT_ACCEPTED,
                    "registration upload media type is not accepted");
        }

        public static Failure sizeLimitExceeded() {
            return new Failure(Category.REGISTRATION_UPLOAD_SIZE_LIMIT_EXCEEDED,
                    "registration upload size limit is exceeded");
        }

        public static Failure signatureMismatch() {
            return new Failure(Category.REGISTRATION_UPLOAD_SIGNATURE_MISMATCH,
                    "registration upload signature does not match media type");
        }

        public static Failure limitReached() {
            return new Failure(Category.REGISTRATION_UPLOAD_LIMIT_REACHED,
                    "registration upload limit is reached");
        }

        public static Failure unavailable() {
            return new Failure(Category.REGISTRATION_UPLOAD_UNAVAILABLE,
                    "registration upload service is unavailable");
        }
    }
}
