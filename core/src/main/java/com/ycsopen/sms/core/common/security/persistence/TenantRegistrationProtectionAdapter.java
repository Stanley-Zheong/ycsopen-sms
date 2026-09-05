package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.key.lifecycle.FieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Sole owner of protected tenant-registration field assignment and staged-object claim.
 *
 * <p>The caller must already have allocated the immutable tenant identity inside its transaction.
 * This adapter authenticates that identity in every field envelope and requires the object/session
 * claim to join the same transaction. No plaintext, upload credential, object metadata, or storage
 * locator is retained by this boundary.</p>
 */
@Component
public final class TenantRegistrationProtectionAdapter {

    public static final String UPLOAD_TOKEN_HEADER =
            TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER;

    private static final String LOGICAL_OWNER = "crypto-storage-bootstrap";
    private static final String LOGICAL_TABLE = "tenants";
    private static final Pattern SESSION_ID = Pattern.compile(
            "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final Pattern OBJECT_ID = Pattern.compile("pobj_v1_[A-Za-z0-9_-]+");
    private static final Pattern CREDIT_CODE = Pattern.compile("[0-9A-Z]{18}");
    private static final Pattern ID_NUMBER = Pattern.compile("[0-9]{17}[0-9Xx]");
    private static final Pattern MOBILE = Pattern.compile("1[3-9][0-9]{9}");
    private static final Pattern HTTP_VALUE = Pattern.compile("(?i)^https?://.*");
    private static final AssignmentPermit ASSIGNMENT_PERMIT = new AssignmentPermit();

    private final ProtectedFieldCodec protectedFieldCodec;
    private final ClaimStore claimStore;
    private final FieldReferencePublicationFence fieldFence;

    @Autowired
    public TenantRegistrationProtectionAdapter(
            KeyProtectionPort keyProtectionPort,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<TenantRegistrationObjectSessionService> sessionServiceProvider,
            ActiveFieldKeyReference activeFieldKeyReference,
            FieldReferencePublicationFence fieldFence) {
        this(new ProtectedFieldCodec(new EnvelopeCodec(), keyProtectionPort,
                        new SecureRandom(), activeFieldKeyReference::current),
                new JdbcClaimStore(jdbcTemplate, sessionServiceProvider::getIfAvailable,
                        Clock.systemUTC()), fieldFence);
    }

    public TenantRegistrationProtectionAdapter(ProtectedFieldCodec protectedFieldCodec,
                                               ClaimStore claimStore,
                                               FieldReferencePublicationFence fieldFence) {
        this.protectedFieldCodec = Objects.requireNonNull(
                protectedFieldCodec, "protectedFieldCodec");
        this.claimStore = Objects.requireNonNull(claimStore, "claimStore");
        this.fieldFence = Objects.requireNonNull(fieldFence, "fieldFence");
    }

    /** Rejects unsafe or incomplete wire input before the first tenant database write. */
    public void validateRequest(TenantRegistrationRequest request, String uploadToken) {
        if (request == null) {
            throw Failure.inputInvalid();
        }
        if (request.hasLegacyObjectUrlInput()
                || isHttp(request.registrationObjectSessionId())
                || isHttp(request.businessLicenseObjectId())
                || isHttp(request.legalRepIdFrontObjectId())
                || isHttp(request.legalRepIdBackObjectId())
                || isHttp(request.shortlinkDomainProofObjectId())
                || isHttp(request.trademarkProofObjectId())) {
            throw Failure.legacyObjectUrlNotAccepted();
        }
        if (request.hasUnknownFields()) {
            throw Failure.unknownField();
        }
        requireText(request.shortName(), 20);
        requireText(request.fullName(), 100);
        requirePattern(request.unifiedSocialCreditCode(), CREDIT_CODE);
        requireText(request.legalRepName(), 50);
        requirePattern(request.legalRepIdNo(), ID_NUMBER);
        requireText(request.contactName(), 50);
        requirePattern(request.contactIdNo(), ID_NUMBER);
        requirePattern(request.contactPhone(), MOBILE);
        if (request.registrationObjectSessionId() == null
                || !SESSION_ID.matcher(request.registrationObjectSessionId()).matches()) {
            throw Failure.inputInvalid();
        }
        if (uploadToken == null || uploadToken.isBlank()) {
            throw Failure.uploadTokenInvalid();
        }
        requireObjectId(request.businessLicenseObjectId(), true);
        requireObjectId(request.legalRepIdFrontObjectId(), true);
        requireObjectId(request.legalRepIdBackObjectId(), true);
        requireObjectId(request.shortlinkDomainProofObjectId(), false);
        requireObjectId(request.trademarkProofObjectId(), false);
    }

    /** Protects all three values and claims the exact selected object set in the caller transaction. */
    public void protectRegistration(Tenant tenant,
                                    TenantRegistrationRequest request,
                                    String uploadToken) {
        validateRequest(request, uploadToken);
        if (tenant == null || tenant.getId() == null || tenant.getId() <= 0
                || tenant.getTenantNo() == null || tenant.getTenantNo().isBlank()) {
            throw Failure.protectionUnavailable();
        }

        byte[] legalPlaintext = ascii(request.legalRepIdNo());
        byte[] contactIdPlaintext = ascii(request.contactIdNo());
        byte[] contactPhonePlaintext = ascii(request.contactPhone());
        byte[] legalEnvelope = null;
        byte[] contactIdEnvelope = null;
        byte[] contactPhoneEnvelope = null;
        try {
            legalEnvelope = protect(tenant, "legal_rep_id_no_encrypted", legalPlaintext);
            contactIdEnvelope = protect(tenant, "contact_id_no_encrypted", contactIdPlaintext);
            contactPhoneEnvelope = protect(tenant, "contact_phone_encrypted", contactPhonePlaintext);

            fieldFence.lockAndValidate(legalEnvelope, EnvelopeCodec.Target.DATABASE_FIELD);
            fieldFence.lockAndValidate(contactIdEnvelope, EnvelopeCodec.Target.DATABASE_FIELD);
            fieldFence.lockAndValidate(contactPhoneEnvelope, EnvelopeCodec.Target.DATABASE_FIELD);

            ObjectSelection selection = new ObjectSelection(
                    request.businessLicenseObjectId(), request.legalRepIdFrontObjectId(),
                    request.legalRepIdBackObjectId(),
                    optionalObjectId(request.shortlinkDomainProofObjectId()),
                    optionalObjectId(request.trademarkProofObjectId()));
            ClaimedObjects claimed = claimStore.claim(tenant.getId(),
                    request.registrationObjectSessionId(), uploadToken, selection);
            if (!ClaimedObjects.from(selection).equals(claimed)) {
                throw Failure.partialClaim();
            }
            PreparedRegistration prepared = new PreparedRegistration(
                    legalEnvelope, contactIdEnvelope, contactPhoneEnvelope,
                    claimed.businessLicenseObjectId(), claimed.legalRepIdFrontObjectId(),
                    claimed.legalRepIdBackObjectId(), claimed.shortlinkDomainProofObjectId(),
                    claimed.trademarkProofObjectId());
            try {
                tenant.assignProtectedRegistration(prepared, ASSIGNMENT_PERMIT);
            } finally {
                prepared.destroy();
            }
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw Failure.protectionUnavailable();
        } finally {
            clear(legalPlaintext);
            clear(contactIdPlaintext);
            clear(contactPhonePlaintext);
            clear(legalEnvelope);
            clear(contactIdEnvelope);
            clear(contactPhoneEnvelope);
        }
    }

    private byte[] protect(Tenant tenant, String field, byte[] plaintext) {
        return protectedFieldCodec.protect(plaintext, new ProtectionContext(
                        ProtectionContext.Purpose.DATABASE_FIELD,
                        LOGICAL_OWNER, LOGICAL_TABLE, field,
                        "tenant:" + tenant.getId(), "tenant_id=" + tenant.getId()),
                EnvelopeCodec.Target.DATABASE_FIELD);
    }

    private static void requireText(String value, int maximumCharacters) {
        if (value == null || value.isBlank() || value.length() > maximumCharacters) {
            throw Failure.inputInvalid();
        }
    }

    private static void requirePattern(String value, Pattern pattern) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw Failure.inputInvalid();
        }
    }

    private static void requireObjectId(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw Failure.objectRequired();
            }
            return;
        }
        if (isHttp(value)) {
            throw Failure.legacyObjectUrlNotAccepted();
        }
        if (value.length() > 80 || !OBJECT_ID.matcher(value).matches()) {
            throw Failure.objectIdInvalid();
        }
    }

    private static boolean isHttp(String value) {
        return value != null && HTTP_VALUE.matcher(value.trim()).matches();
    }

    private static String optionalObjectId(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    public record ObjectSelection(String businessLicenseObjectId,
                                  String legalRepIdFrontObjectId,
                                  String legalRepIdBackObjectId,
                                  String shortlinkDomainProofObjectId,
                                  String trademarkProofObjectId) {
        List<SelectedObject> selected() {
            List<SelectedObject> selected = new ArrayList<>();
            selected.add(new SelectedObject(businessLicenseObjectId, ObjectPurpose.BUSINESS_LICENSE));
            selected.add(new SelectedObject(legalRepIdFrontObjectId,
                    ObjectPurpose.LEGAL_REPRESENTATIVE_ID_FRONT));
            selected.add(new SelectedObject(legalRepIdBackObjectId,
                    ObjectPurpose.LEGAL_REPRESENTATIVE_ID_BACK));
            if (shortlinkDomainProofObjectId != null && !shortlinkDomainProofObjectId.isBlank()) {
                selected.add(new SelectedObject(shortlinkDomainProofObjectId,
                        ObjectPurpose.SHORT_LINK_PROOF));
            }
            if (trademarkProofObjectId != null && !trademarkProofObjectId.isBlank()) {
                selected.add(new SelectedObject(trademarkProofObjectId,
                        ObjectPurpose.TRADEMARK_PROOF));
            }
            return List.copyOf(selected);
        }
    }

    public record ClaimedObjects(String businessLicenseObjectId,
                                 String legalRepIdFrontObjectId,
                                 String legalRepIdBackObjectId,
                                 String shortlinkDomainProofObjectId,
                                 String trademarkProofObjectId) {
        static ClaimedObjects from(ObjectSelection selected) {
            return new ClaimedObjects(selected.businessLicenseObjectId(),
                    selected.legalRepIdFrontObjectId(), selected.legalRepIdBackObjectId(),
                    selected.shortlinkDomainProofObjectId(), selected.trademarkProofObjectId());
        }
    }

    /** Immutable, defensively copied handoff; rendering never exposes bytes or object IDs. */
    public static final class PreparedRegistration {
        private final byte[] legalRepresentativeIdEnvelope;
        private final byte[] contactIdEnvelope;
        private final byte[] contactPhoneEnvelope;
        private final String businessLicenseObjectId;
        private final String legalRepIdFrontObjectId;
        private final String legalRepIdBackObjectId;
        private final String shortlinkDomainProofObjectId;
        private final String trademarkProofObjectId;

        private PreparedRegistration(byte[] legalRepresentativeIdEnvelope,
                                     byte[] contactIdEnvelope,
                                     byte[] contactPhoneEnvelope,
                                     String businessLicenseObjectId,
                                     String legalRepIdFrontObjectId,
                                     String legalRepIdBackObjectId,
                                     String shortlinkDomainProofObjectId,
                                     String trademarkProofObjectId) {
            this.legalRepresentativeIdEnvelope = legalRepresentativeIdEnvelope.clone();
            this.contactIdEnvelope = contactIdEnvelope.clone();
            this.contactPhoneEnvelope = contactPhoneEnvelope.clone();
            this.businessLicenseObjectId = businessLicenseObjectId;
            this.legalRepIdFrontObjectId = legalRepIdFrontObjectId;
            this.legalRepIdBackObjectId = legalRepIdBackObjectId;
            this.shortlinkDomainProofObjectId = shortlinkDomainProofObjectId;
            this.trademarkProofObjectId = trademarkProofObjectId;
        }

        public byte[] copyLegalRepresentativeIdEnvelope() {
            return legalRepresentativeIdEnvelope.clone();
        }

        public byte[] copyContactIdEnvelope() {
            return contactIdEnvelope.clone();
        }

        public byte[] copyContactPhoneEnvelope() {
            return contactPhoneEnvelope.clone();
        }

        public String businessLicenseObjectId() { return businessLicenseObjectId; }
        public String legalRepIdFrontObjectId() { return legalRepIdFrontObjectId; }
        public String legalRepIdBackObjectId() { return legalRepIdBackObjectId; }
        public String shortlinkDomainProofObjectId() { return shortlinkDomainProofObjectId; }
        public String trademarkProofObjectId() { return trademarkProofObjectId; }

        private void destroy() {
            clear(legalRepresentativeIdEnvelope);
            clear(contactIdEnvelope);
            clear(contactPhoneEnvelope);
        }

        @Override
        public String toString() {
            return "PreparedRegistration[protectedFields=[redacted], objects=[redacted]]";
        }
    }

    /** Testable claim boundary; production implementation requires an already active transaction. */
    @FunctionalInterface
    public interface ClaimStore {
        ClaimedObjects claim(long tenantId, String registrationSessionId,
                             String uploadToken, ObjectSelection selection);
    }

    /** JDBC claim owner using row locks and the Plan-17 credential verifier. */
    static final class JdbcClaimStore implements ClaimStore {
        private final JdbcTemplate jdbc;
        private final Supplier<TenantRegistrationObjectSessionService> sessionService;
        private final Clock clock;

        private JdbcClaimStore(JdbcTemplate jdbc,
                               Supplier<TenantRegistrationObjectSessionService> sessionService,
                               Clock clock) {
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        @Override
        public ClaimedObjects claim(long tenantId, String registrationSessionId,
                                    String uploadToken, ObjectSelection selection) {
            if (tenantId <= 0 || !TransactionSynchronizationManager.isActualTransactionActive()) {
                throw Failure.protectionUnavailable();
            }
            try {
                SessionRow session = lockSession(registrationSessionId);
                Instant now = clock.instant();
                if (!now.isBefore(session.expiresAt())) {
                    throw Failure.sessionExpired();
                }
                if (!"OPEN".equals(session.state())) {
                    throw Failure.sessionNotOpen();
                }

                List<SelectedObject> selected = selection.selected().stream()
                        .sorted(Comparator.comparing(SelectedObject::objectId)).toList();
                Set<String> uniqueIds = new HashSet<>();
                for (SelectedObject expected : selected) {
                    if (!uniqueIds.add(expected.objectId())) {
                        throw Failure.objectBindingMismatch();
                    }
                    ObjectRow actual = lockObject(expected.objectId());
                    validateObject(actual, expected, registrationSessionId,
                            session.tenantDraftId(), now);
                }

                TenantRegistrationObjectSessionService verifier = sessionService.get();
                if (verifier == null) {
                    throw Failure.protectionUnavailable();
                }
                try {
                    verifier.claim(registrationSessionId, uploadToken);
                } catch (TenantRegistrationObjectSessionService.Failure failure) {
                    throw mapSessionFailure(failure);
                }

                for (SelectedObject expected : selected) {
                    // The schema requires a globally unique reference per object. Keep it opaque:
                    // tenant identity and purpose are already transactionally bound in the row,
                    // and a rollback discards this fresh value with every state transition.
                    String claimReference = "claim_v1_"
                            + UUID.randomUUID().toString().replace("-", "");
                    int changed = jdbc.update("""
                            UPDATE ycs_crypto_protected_objects
                               SET object_state = 'CLAIMED', claim_reference = ?,
                                   optimistic_version = optimistic_version + 1
                             WHERE protected_object_id = ?
                               AND registration_session_id = ?
                               AND tenant_draft_id = ?
                               AND object_purpose = ?
                               AND object_state = 'STAGED'
                            """, claimReference, expected.objectId(), registrationSessionId,
                            session.tenantDraftId(), expected.purpose().name());
                    if (changed != 1) {
                        throw Failure.partialClaim();
                    }
                }
                return ClaimedObjects.from(selection);
            } catch (Failure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw Failure.protectionUnavailable();
            }
        }

        private SessionRow lockSession(String registrationSessionId) {
            List<SessionRow> rows = jdbc.query("""
                    SELECT tenant_draft_id, session_state, expires_at
                      FROM ycs_crypto_registration_sessions
                     WHERE registration_session_id = ?
                     FOR UPDATE
                    """, (rs, row) -> new SessionRow(rs.getString("tenant_draft_id"),
                    rs.getString("session_state"), rs.getTimestamp("expires_at").toInstant()),
                    registrationSessionId);
            if (rows.size() != 1) {
                throw Failure.objectBindingMismatch();
            }
            return rows.getFirst();
        }

        private ObjectRow lockObject(String objectId) {
            List<ObjectRow> rows = jdbc.query("""
                    SELECT protected_object_id, registration_session_id, tenant_draft_id,
                           object_purpose, object_state, envelope_size, media_type, expires_at
                      FROM ycs_crypto_protected_objects
                     WHERE protected_object_id = ?
                     FOR UPDATE
                    """, (rs, row) -> new ObjectRow(rs.getString("protected_object_id"),
                    rs.getString("registration_session_id"), rs.getString("tenant_draft_id"),
                    ObjectPurpose.valueOf(rs.getString("object_purpose")),
                    rs.getString("object_state"), rs.getLong("envelope_size"),
                    rs.getString("media_type"), rs.getTimestamp("expires_at").toInstant()), objectId);
            if (rows.size() != 1) {
                throw Failure.objectBindingMismatch();
            }
            return rows.getFirst();
        }

        private static void validateObject(ObjectRow actual, SelectedObject expected,
                                           String sessionId, String tenantDraftId, Instant now) {
            if ("CLAIMED".equals(actual.state())) {
                throw Failure.objectAlreadyClaimed();
            }
            if (!"STAGED".equals(actual.state())) {
                throw Failure.objectNotStaged();
            }
            if (!now.isBefore(actual.expiresAt())) {
                throw Failure.objectExpired();
            }
            if (!sessionId.equals(actual.sessionId())
                    || !tenantDraftId.equals(actual.tenantDraftId())
                    || actual.purpose() != expected.purpose()) {
                throw Failure.objectBindingMismatch();
            }
            if (!actual.purpose().accepts(actual.mediaType())) {
                throw Failure.objectMediaMismatch();
            }
            if (actual.envelopeSize() < 1
                    || actual.envelopeSize() > actual.purpose().maximumEnvelopeBytes()) {
                throw Failure.objectSizeInvalid();
            }
        }

        private static Failure mapSessionFailure(
                TenantRegistrationObjectSessionService.Failure failure) {
            return switch (failure.category()) {
                case REGISTRATION_UPLOAD_TOKEN_INVALID -> Failure.uploadTokenInvalid();
                case REGISTRATION_UPLOAD_SESSION_NOT_OPEN -> Failure.sessionNotOpen();
                case REGISTRATION_UPLOAD_SESSION_EXPIRED -> Failure.sessionExpired();
                default -> Failure.protectionUnavailable();
            };
        }

        private record SessionRow(String tenantDraftId, String state, Instant expiresAt) {
        }

        private record ObjectRow(String objectId, String sessionId, String tenantDraftId,
                                 ObjectPurpose purpose, String state, long envelopeSize,
                                 String mediaType, Instant expiresAt) {
        }
    }

    private record SelectedObject(String objectId, ObjectPurpose purpose) {
    }

    private enum ObjectPurpose {
        BUSINESS_LICENSE(10_485_905L, Set.of("application/pdf", "image/jpeg", "image/png")),
        LEGAL_REPRESENTATIVE_ID_FRONT(5_243_025L, Set.of("image/jpeg", "image/png")),
        LEGAL_REPRESENTATIVE_ID_BACK(5_243_025L, Set.of("image/jpeg", "image/png")),
        SHORT_LINK_PROOF(10_485_905L, Set.of("application/pdf", "image/jpeg", "image/png")),
        TRADEMARK_PROOF(10_485_905L, Set.of("application/pdf", "image/jpeg", "image/png"));

        private final long maximumEnvelopeBytes;
        private final Set<String> mediaTypes;

        ObjectPurpose(long maximumEnvelopeBytes, Set<String> mediaTypes) {
            this.maximumEnvelopeBytes = maximumEnvelopeBytes;
            this.mediaTypes = mediaTypes;
        }

        long maximumEnvelopeBytes() {
            return maximumEnvelopeBytes;
        }

        boolean accepts(String mediaType) {
            return mediaType != null && mediaTypes.contains(mediaType);
        }
    }

    /** Unforgeable cross-package permit keeping protected entity mutation adapter-owned. */
    public static final class AssignmentPermit {
        private AssignmentPermit() {
        }
    }

    /** Stable cause-free boundary; values and provider/storage diagnostics never escape. */
    public static final class Failure extends RuntimeException {
        public enum Category {
            REGISTRATION_INPUT_INVALID,
            REGISTRATION_UNKNOWN_FIELD,
            LEGACY_OBJECT_URL_NOT_ACCEPTED,
            REGISTRATION_OBJECT_REQUIRED,
            REGISTRATION_OBJECT_ID_INVALID,
            REGISTRATION_UPLOAD_TOKEN_INVALID,
            REGISTRATION_OBJECT_SESSION_NOT_OPEN,
            REGISTRATION_OBJECT_SESSION_EXPIRED,
            REGISTRATION_OBJECT_BINDING_MISMATCH,
            REGISTRATION_OBJECT_ALREADY_CLAIMED,
            REGISTRATION_OBJECT_NOT_STAGED,
            REGISTRATION_OBJECT_EXPIRED,
            REGISTRATION_OBJECT_MEDIA_MISMATCH,
            REGISTRATION_OBJECT_SIZE_INVALID,
            REGISTRATION_OBJECT_PARTIAL_CLAIM,
            REGISTRATION_PROTECTION_UNAVAILABLE
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
            return failure(Category.REGISTRATION_INPUT_INVALID,
                    "tenant registration input is invalid");
        }

        public static Failure unknownField() {
            return failure(Category.REGISTRATION_UNKNOWN_FIELD,
                    "tenant registration contains an unknown field");
        }

        public static Failure legacyObjectUrlNotAccepted() {
            return failure(Category.LEGACY_OBJECT_URL_NOT_ACCEPTED,
                    "legacy object URL input is not accepted");
        }

        public static Failure objectRequired() {
            return failure(Category.REGISTRATION_OBJECT_REQUIRED,
                    "required registration object is missing");
        }

        public static Failure objectIdInvalid() {
            return failure(Category.REGISTRATION_OBJECT_ID_INVALID,
                    "registration object identity is invalid");
        }

        public static Failure uploadTokenInvalid() {
            return failure(Category.REGISTRATION_UPLOAD_TOKEN_INVALID,
                    "registration upload credential is invalid");
        }

        public static Failure sessionNotOpen() {
            return failure(Category.REGISTRATION_OBJECT_SESSION_NOT_OPEN,
                    "registration object session is not open");
        }

        public static Failure sessionExpired() {
            return failure(Category.REGISTRATION_OBJECT_SESSION_EXPIRED,
                    "registration object session has expired");
        }

        public static Failure objectBindingMismatch() {
            return failure(Category.REGISTRATION_OBJECT_BINDING_MISMATCH,
                    "registration object binding does not match");
        }

        public static Failure objectAlreadyClaimed() {
            return failure(Category.REGISTRATION_OBJECT_ALREADY_CLAIMED,
                    "registration object was already claimed");
        }

        public static Failure objectNotStaged() {
            return failure(Category.REGISTRATION_OBJECT_NOT_STAGED,
                    "registration object is not staged");
        }

        public static Failure objectExpired() {
            return failure(Category.REGISTRATION_OBJECT_EXPIRED,
                    "registration object has expired");
        }

        public static Failure objectMediaMismatch() {
            return failure(Category.REGISTRATION_OBJECT_MEDIA_MISMATCH,
                    "registration object media does not match its purpose");
        }

        public static Failure objectSizeInvalid() {
            return failure(Category.REGISTRATION_OBJECT_SIZE_INVALID,
                    "registration object size is invalid");
        }

        public static Failure partialClaim() {
            return failure(Category.REGISTRATION_OBJECT_PARTIAL_CLAIM,
                    "registration object claim was incomplete");
        }

        public static Failure protectionUnavailable() {
            return failure(Category.REGISTRATION_PROTECTION_UNAVAILABLE,
                    "tenant registration protection is unavailable");
        }

        private static Failure failure(Category category, String message) {
            return new Failure(category, message);
        }
    }
}
