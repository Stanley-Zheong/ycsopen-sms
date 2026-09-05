package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.envelope.ProtectionFailure;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sole lifecycle owner for encrypted registration objects.
 *
 * <p>Writes read at most the purpose ceiling plus one byte, encrypt the complete value, and only
 * then call the private store. Reads validate capability and current authorization before metadata,
 * HEAD, body, checksum, or key access. Plaintext is returned only after the complete GCM tag passes.</p>
 */
public final class ProtectedObjectService {

    private static final String LOGICAL_OWNER = "crypto-storage-bootstrap";
    private static final String LOGICAL_CLASS = "registration-object";
    private static final int OBJECT_ID_RANDOM_BYTES = 24;
    private static final int COPY_BUFFER_BYTES = 8_192;
    private static final long MAXIMUM_U32 = 0xffff_ffffL;

    private final ProtectedFieldCodec protectedFieldCodec;
    private final PrivateObjectStorePort objectStore;
    private final ProtectedObjectMetadataRepository metadataRepository;
    private final ObjectCapabilityService capabilityService;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public ProtectedObjectService(ProtectedFieldCodec protectedFieldCodec,
                                  PrivateObjectStorePort objectStore,
                                  ProtectedObjectMetadataRepository metadataRepository,
                                  ObjectCapabilityService capabilityService,
                                  SecureRandom secureRandom,
                                  Clock clock) {
        this.protectedFieldCodec = Objects.requireNonNull(protectedFieldCodec, "protectedFieldCodec");
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore");
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Encrypts one complete bounded object before any private-store put. */
    public CreatedObject create(CreateRequest request) {
        requireCreateRequest(request);
        byte[] plaintext = null;
        byte[] envelope = null;
        ProtectedObjectMetadataRepository.CreateOperation operation = null;
        StoredObjectMetadata stored = null;
        try {
            plaintext = readPlaintext(request.input(), request.declaredPlaintextLength(), request.purpose());
            String protectedObjectId = newObjectId();
            String operationId = newOperationId();
            operation = new ProtectedObjectMetadataRepository.CreateOperation(
                    operationId, protectedObjectId, request.registrationSessionId(),
                    request.tenantDraftId(), request.purpose(), request.attemptNumber(),
                    request.expiresAt(), request.replacesObjectId());

            ProtectionContext context = context(operation);
            envelope = protectedFieldCodec.protect(
                    plaintext, context, request.purpose().envelopeTarget());
            requireCompleteEnvelope(envelope, request.purpose());

            metadataRepository.beginCreate(operation, envelope);
            try {
                stored = objectStore.put(request.purpose(), request.mediaType(),
                        new ByteArrayInputStream(envelope), (long) envelope.length);
            } catch (RuntimeException storeFailure) {
                // A provider exception can occur after the remote write. Without a returned
                // locator absence is not proven, so retain the FIELD reservation fail-closed.
                failOperation(operation.operationId(), false);
                throw mapStoreFailure(storeFailure);
            }
            try {
                requireStoreWrite(stored, envelope, request.purpose(), request.mediaType());
            } catch (Failure invalidStoreResult) {
                containUndurableSplitWrite(operation, stored);
                throw invalidStoreResult;
            }
            try {
                metadataRepository.recordObjectStored(operation, stored);
            } catch (RuntimeException journalFailure) {
                containUndurableSplitWrite(operation, stored);
                throw Failure.unavailable();
            }

            Optional<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> replaced;
            try {
                replaced = metadataRepository.completeCreate(operation, stored);
            } catch (RuntimeException metadataFailure) {
                containSplitWrite(operation, stored);
                throw Failure.unavailable();
            }
            replaced.ifPresent(this::deleteReplacedOrLeaveForReconciliation);
            return new CreatedObject(protectedObjectId, request.purpose(), request.mediaType(),
                    plaintext.length, request.expiresAt());
        } catch (Failure failure) {
            throw failure;
        } catch (ProtectionFailure failure) {
            throw Failure.invalidInput();
        } catch (RuntimeException failure) {
            if (operation != null && stored == null) {
                failOperation(operation.operationId(), false);
            }
            throw Failure.unavailable();
        } finally {
            clear(plaintext);
            clear(envelope);
        }
    }

    /**
     * Returns plaintext only after capability, authorization, safe metadata, HEAD, exact body,
     * checksum, strict envelope, and complete GCM authentication all succeed.
     */
    public ProtectedObjectData read(ReadRequest request) {
        requireReadRequest(request);
        try {
            ObjectCapabilityService.AccessRequest access = new ObjectCapabilityService.AccessRequest(
                    request.protectedObjectId(), request.tenantScope(), request.subject(),
                    request.accessPurpose());
            return capabilityService.authorizeAndFetch(request.capabilityToken(), access,
                    () -> readAuthorized(request));
        } catch (ObjectCapabilityService.Failure denied) {
            throw Failure.denied();
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
    }

    /** Deletes a known object or leaves an opaque reconciliation record on provider failure. */
    public void delete(DeleteRequest request) {
        requireDeleteRequest(request);
        ProtectedObjectMetadataRepository.ProtectedObjectMetadata metadata;
        try {
            metadata = metadataRepository.reserveDelete(request.protectedObjectId(), request.purpose(),
                            tenantDraftId(request.tenantScope()))
                    .orElseThrow(Failure::invalidInput);
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
        try {
            objectStore.delete(metadata.storageKey(), metadata.purpose());
            metadataRepository.markDeleted(metadata.protectedObjectId());
        } catch (RuntimeException failure) {
            // The durable DELETING row owns reconciliation; claim can no longer win this race.
            throw mapStoreFailure(failure);
        }
    }

    /** Replays bounded deterministic deletion of replaced, expired, or orphaned ciphertext. */
    public ReconciliationResult reconcile(int limit) {
        List<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> candidates;
        try {
            candidates = metadataRepository.reconciliationCandidates(limit);
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
        int deleted = 0;
        for (ProtectedObjectMetadataRepository.ProtectedObjectMetadata candidate : candidates) {
            try {
                Optional<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> reserved =
                        candidate.state() == ProtectedObjectMetadataRepository.ObjectState.DELETING
                                ? Optional.of(candidate)
                                : metadataRepository.reserveDelete(candidate.protectedObjectId(),
                                candidate.purpose(), candidate.tenantDraftId());
                if (reserved.isEmpty()) {
                    continue;
                }
                objectStore.delete(reserved.get().storageKey(), reserved.get().purpose());
                metadataRepository.markDeleted(reserved.get().protectedObjectId());
                deleted++;
            } catch (RuntimeException ignored) {
                // Keep the safe metadata row in a retryable state; never expose provider text.
            }
        }
        return new ReconciliationResult(candidates.size(), deleted);
    }

    private ProtectedObjectData readAuthorized(ReadRequest request) {
        ProtectedObjectMetadataRepository.ProtectedObjectMetadata expected;
        try {
            expected = metadataRepository.find(request.protectedObjectId())
                    .orElseThrow(Failure::denied);
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
        if (expected.purpose() != request.objectPurpose()
                || !tenantScope(expected.tenantDraftId()).equals(request.tenantScope())
                || expected.state() != ProtectedObjectMetadataRepository.ObjectState.STAGED
                && expected.state() != ProtectedObjectMetadataRepository.ObjectState.CLAIMED
                || !clock.instant().isBefore(expected.expiresAt())) {
            throw Failure.denied();
        }

        PrivateObjectStorePort.StoredCiphertext storedCiphertext;
        byte[] envelope = null;
        try {
            StoredObjectMetadata head = objectStore.head(expected.storageKey(), expected.purpose());
            requireMatchingMetadata(expected, head);
            storedCiphertext = objectStore.get(expected.storageKey(), expected.purpose());
            requireMatchingMetadata(expected, storedCiphertext.metadata());
            envelope = storedCiphertext.ciphertext();
            requireCompleteEnvelope(envelope, expected.purpose());
            if (!constantTimeAsciiEquals(sha256Hex(envelope), expected.envelopeSha256())) {
                throw Failure.integrity();
            }
            byte[] plaintext = protectedFieldCodec.unprotect(
                    envelope, context(expected), expected.purpose().envelopeTarget());
            try {
                if (plaintext.length > expected.purpose().envelopeTarget().maximumPlaintextBytes()) {
                    throw Failure.integrity();
                }
                return new ProtectedObjectData(plaintext, expected.mediaType(), expected.purpose());
            } finally {
                clear(plaintext);
            }
        } catch (Failure failure) {
            throw failure;
        } catch (ProtectionFailure failure) {
            throw Failure.integrity();
        } catch (PrivateObjectStorePort.Failure failure) {
            throw mapStoreFailure(failure);
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        } finally {
            clear(envelope);
        }
    }

    private void containSplitWrite(ProtectedObjectMetadataRepository.CreateOperation operation,
                                   StoredObjectMetadata stored) {
        try {
            objectStore.delete(stored.storageKey(), stored.purpose());
            metadataRepository.reserveDelete(operation.protectedObjectId(), operation.purpose(),
                            operation.tenantDraftId())
                    .ifPresent(value -> metadataRepository.markDeleted(value.protectedObjectId()));
            return;
        } catch (RuntimeException ignored) {
            // Persist the safe locator under the deterministic operation if immediate delete failed.
        }
        try {
            metadataRepository.recordOrphan(operation, stored);
        } catch (RuntimeException ignored) {
            // Both providers are unavailable; the stable failure contains neither diagnostic.
        }
    }

    private void containUndurableSplitWrite(
            ProtectedObjectMetadataRepository.CreateOperation operation,
            StoredObjectMetadata stored) {
        boolean absenceConfirmed = false;
        try {
            objectStore.delete(stored.storageKey(), stored.purpose());
            absenceConfirmed = true;
        } catch (RuntimeException ignored) {
            // Retain the FIELD reservation until reconciliation proves the object absent.
        }
        failOperation(operation.operationId(), absenceConfirmed);
    }

    private void deleteReplacedOrLeaveForReconciliation(
            ProtectedObjectMetadataRepository.ProtectedObjectMetadata replaced) {
        try {
            Optional<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> reserved =
                    metadataRepository.reserveDelete(replaced.protectedObjectId(),
                            replaced.purpose(), replaced.tenantDraftId());
            if (reserved.isPresent()) {
                objectStore.delete(reserved.get().storageKey(), reserved.get().purpose());
                metadataRepository.markDeleted(reserved.get().protectedObjectId());
            }
        } catch (RuntimeException ignored) {
            // REPLACED or DELETING remains a deterministic reconciliation state.
        }
    }

    private void failOperation(String operationId, boolean absenceConfirmed) {
        try {
            metadataRepository.failCreate(operationId, absenceConfirmed);
        } catch (RuntimeException ignored) {
            // Do not replace the stable service failure with repository or provider diagnostics.
        }
    }

    private static byte[] readPlaintext(InputStream input,
                                        Long declaredLength,
                                        PrivateObjectStorePort.ObjectPurpose purpose) {
        long maximum = purpose.envelopeTarget().maximumPlaintextBytes();
        if (input == null || declaredLength != null
                && (declaredLength < 0 || declaredLength > maximum || declaredLength > MAXIMUM_U32)) {
            throw Failure.invalidInput();
        }
        int allocation = declaredLength == null
                ? Math.min(COPY_BUFFER_BYTES, Math.toIntExact(maximum))
                : Math.toIntExact(declaredLength);
        int readLimit = Math.toIntExact(Math.addExact(maximum, 1L));
        ByteArrayOutputStream output = new ByteArrayOutputStream(allocation);
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try {
            while (output.size() < readLimit) {
                int remaining = readLimit - output.size();
                int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count == -1) {
                    break;
                }
                if (count == 0) {
                    int one = input.read();
                    if (one == -1) {
                        break;
                    }
                    output.write(one);
                } else {
                    output.write(buffer, 0, count);
                }
            }
            if (output.size() > maximum || input.read() != -1
                    || declaredLength != null && output.size() != declaredLength) {
                throw Failure.invalidInput();
            }
            return output.toByteArray();
        } catch (IOException | ArithmeticException failure) {
            throw Failure.invalidInput();
        } finally {
            clear(buffer);
        }
    }

    private static void requireCompleteEnvelope(byte[] envelope,
                                                PrivateObjectStorePort.ObjectPurpose purpose) {
        if (envelope == null || envelope.length < EnvelopeCodec.FIXED_HEADER_BYTES
                || envelope.length > purpose.maximumEnvelopeBytes()) {
            throw Failure.integrity();
        }
    }

    private static void requireStoreWrite(StoredObjectMetadata stored,
                                          byte[] envelope,
                                          PrivateObjectStorePort.ObjectPurpose purpose,
                                          String mediaType) {
        if (stored == null || stored.purpose() != purpose || stored.size() != envelope.length
                || stored.size() > purpose.maximumEnvelopeBytes()
                || !mediaType.equals(stored.mediaType())
                || stored.storageKey() == null
                || !stored.storageKey().matches("obj_v1_[0-9a-f]{64}")
                || !constantTimeAsciiEquals(stored.sha256(), sha256Hex(envelope))) {
            throw Failure.integrity();
        }
    }

    private static void requireMatchingMetadata(
            ProtectedObjectMetadataRepository.ProtectedObjectMetadata expected,
            StoredObjectMetadata actual) {
        if (actual == null || actual.purpose() != expected.purpose()
                || actual.size() != expected.envelopeSize()
                || actual.size() < EnvelopeCodec.FIXED_HEADER_BYTES
                || actual.size() > expected.purpose().maximumEnvelopeBytes()
                || !actual.mediaType().equals(expected.mediaType())
                || !constantTimeAsciiEquals(actual.sha256(), expected.envelopeSha256())) {
            throw Failure.integrity();
        }
    }

    private static boolean constantTimeAsciiEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw Failure.unavailable();
        }
    }

    private static ProtectionContext context(
            ProtectedObjectMetadataRepository.CreateOperation operation) {
        return new ProtectionContext(ProtectionContext.Purpose.PROTECTED_OBJECT,
                LOGICAL_OWNER, LOGICAL_CLASS, contentRole(operation.purpose()),
                tenantScope(operation.tenantDraftId()), operation.protectedObjectId());
    }

    private static ProtectionContext context(
            ProtectedObjectMetadataRepository.ProtectedObjectMetadata metadata) {
        return new ProtectionContext(ProtectionContext.Purpose.PROTECTED_OBJECT,
                LOGICAL_OWNER, LOGICAL_CLASS, contentRole(metadata.purpose()),
                tenantScope(metadata.tenantDraftId()), metadata.protectedObjectId());
    }

    private static String contentRole(PrivateObjectStorePort.ObjectPurpose purpose) {
        return switch (purpose) {
            case BUSINESS_LICENSE -> "business-license";
            case REPRESENTATIVE_ID_FRONT -> "representative-id-front";
            case REPRESENTATIVE_ID_BACK -> "representative-id-back";
            case SHORT_LINK_DOMAIN_PROOF -> "short-link-domain-proof";
            case TRADEMARK_PROOF -> "trademark-proof";
        };
    }

    private static String tenantScope(String tenantDraftId) {
        return "tenant:" + tenantDraftId;
    }

    private static String tenantDraftId(String tenantScope) {
        String tenantDraftId = tenantScope.substring("tenant:".length());
        requireUuid(tenantDraftId);
        return tenantDraftId;
    }

    private String newObjectId() {
        byte[] random = new byte[OBJECT_ID_RANDOM_BYTES];
        try {
            secureRandom.nextBytes(random);
            return "pobj_v1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        } catch (ProviderException failure) {
            throw Failure.unavailable();
        } finally {
            clear(random);
        }
    }

    private String newOperationId() {
        byte[] random = new byte[16];
        try {
            secureRandom.nextBytes(random);
            random[6] = (byte) ((random[6] & 0x0f) | 0x40);
            random[8] = (byte) ((random[8] & 0x3f) | 0x80);
            long high = 0;
            long low = 0;
            for (int index = 0; index < 8; index++) {
                high = (high << 8) | (random[index] & 0xffL);
                low = (low << 8) | (random[index + 8] & 0xffL);
            }
            return new UUID(high, low).toString();
        } catch (ProviderException failure) {
            throw Failure.unavailable();
        } finally {
            clear(random);
        }
    }

    private void requireCreateRequest(CreateRequest request) {
        if (request == null || request.purpose() == null || request.input() == null
                || request.expiresAt() == null || request.attemptNumber() < 1
                || request.attemptNumber() > 3) {
            throw Failure.invalidInput();
        }
        requireUuid(request.registrationSessionId());
        requireUuid(request.tenantDraftId());
        if (!clock.instant().isBefore(request.expiresAt())) {
            throw Failure.invalidInput();
        }
        requireMediaType(request.purpose(), request.mediaType());
        if (request.replacesObjectId() != null) {
            requireObjectId(request.replacesObjectId());
        }
    }

    private static void requireReadRequest(ReadRequest request) {
        if (request == null) {
            throw Failure.invalidInput();
        }
        requireObjectId(request.protectedObjectId());
        if (request.capabilityToken() == null || request.capabilityToken().isBlank()
                || request.tenantScope() == null || !request.tenantScope().startsWith("tenant:")
                || request.subject() == null || request.subject().isBlank()
                || request.accessPurpose() == null || request.accessPurpose().isBlank()
                || request.objectPurpose() == null) {
            throw Failure.invalidInput();
        }
    }

    private static void requireDeleteRequest(DeleteRequest request) {
        if (request == null || request.purpose() == null || request.tenantScope() == null
                || !request.tenantScope().startsWith("tenant:")) {
            throw Failure.invalidInput();
        }
        requireObjectId(request.protectedObjectId());
        tenantDraftId(request.tenantScope());
    }

    private static void requireUuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw Failure.invalidInput();
        }
    }

    private static void requireObjectId(String value) {
        if (value == null || !value.matches("pobj_v1_[A-Za-z0-9_-]{32}")) {
            throw Failure.invalidInput();
        }
    }

    private static void requireMediaType(PrivateObjectStorePort.ObjectPurpose purpose,
                                         String mediaType) {
        boolean admitted = switch (purpose) {
            case REPRESENTATIVE_ID_FRONT, REPRESENTATIVE_ID_BACK ->
                    "image/jpeg".equals(mediaType) || "image/png".equals(mediaType);
            case BUSINESS_LICENSE, SHORT_LINK_DOMAIN_PROOF, TRADEMARK_PROOF ->
                    "application/pdf".equals(mediaType)
                            || "image/jpeg".equals(mediaType) || "image/png".equals(mediaType);
        };
        if (!admitted) {
            throw Failure.invalidInput();
        }
    }

    private static Failure mapStoreFailure(RuntimeException failure) {
        if (failure instanceof PrivateObjectStorePort.Failure storeFailure
                && storeFailure.category()
                == PrivateObjectStorePort.Failure.Category.OBJECT_INTEGRITY_INVALID) {
            return Failure.integrity();
        }
        if (failure instanceof PrivateObjectStorePort.Failure storeFailure
                && storeFailure.category()
                == PrivateObjectStorePort.Failure.Category.OBJECT_INPUT_INVALID) {
            return Failure.invalidInput();
        }
        return Failure.unavailable();
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    public record CreateRequest(String registrationSessionId,
                                String tenantDraftId,
                                PrivateObjectStorePort.ObjectPurpose purpose,
                                String mediaType,
                                InputStream input,
                                Long declaredPlaintextLength,
                                int attemptNumber,
                                Instant expiresAt,
                                String replacesObjectId) {
        @Override
        public String toString() {
            return "CreateRequest[binding=[redacted], purpose=" + purpose + ", mediaType="
                    + mediaType + ", input=[redacted], declaredPlaintextLength="
                    + declaredPlaintextLength + ", attemptNumber=" + attemptNumber
                    + ", expiresAt=" + expiresAt + ", replaces=[redacted]]";
        }
    }

    public record ReadRequest(String protectedObjectId,
                              String capabilityToken,
                              String tenantScope,
                              String subject,
                              String accessPurpose,
                              PrivateObjectStorePort.ObjectPurpose objectPurpose) {
        @Override
        public String toString() {
            return "ReadRequest[protectedObjectId=" + protectedObjectId
                    + ", capabilityToken=[redacted], bindings=[redacted], accessPurpose="
                    + accessPurpose + ", objectPurpose=" + objectPurpose + "]";
        }
    }

    public record DeleteRequest(String protectedObjectId,
                                String tenantScope,
                                PrivateObjectStorePort.ObjectPurpose purpose) {
        @Override
        public String toString() {
            return "DeleteRequest[protectedObjectId=" + protectedObjectId
                    + ", binding=[redacted], purpose=" + purpose + "]";
        }
    }

    public record CreatedObject(String protectedObjectId,
                                PrivateObjectStorePort.ObjectPurpose purpose,
                                String mediaType,
                                long plaintextSize,
                                Instant expiresAt) {
    }

    public static final class ProtectedObjectData {
        private final byte[] bytes;
        private final String mediaType;
        private final PrivateObjectStorePort.ObjectPurpose purpose;

        public ProtectedObjectData(byte[] bytes,
                                   String mediaType,
                                   PrivateObjectStorePort.ObjectPurpose purpose) {
            this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
            this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
            this.purpose = Objects.requireNonNull(purpose, "purpose");
        }

        public byte[] bytes() {
            return bytes.clone();
        }

        public String mediaType() {
            return mediaType;
        }

        public PrivateObjectStorePort.ObjectPurpose purpose() {
            return purpose;
        }

        @Override
        public String toString() {
            return "ProtectedObjectData[bytes=[redacted], mediaType=" + mediaType
                    + ", purpose=" + purpose + "]";
        }
    }

    public record ReconciliationResult(int examined, int deleted) {
        public ReconciliationResult {
            if (examined < 0 || deleted < 0 || deleted > examined) {
                throw new IllegalArgumentException("reconciliation result is invalid");
            }
        }
    }

    /** Stable, cause-free boundary that cannot retain provider, token, locator, or body text. */
    public static final class Failure extends RuntimeException {
        public enum Category {
            PROTECTED_OBJECT_INPUT_INVALID,
            PROTECTED_OBJECT_ACCESS_DENIED,
            PROTECTED_OBJECT_INTEGRITY_INVALID,
            PROTECTED_OBJECT_UNAVAILABLE
        }

        private final Category category;

        private Failure(Category category, String message) {
            super(message, null, false, false);
            this.category = category;
        }

        public Category category() {
            return category;
        }

        public static Failure invalidInput() {
            return new Failure(Category.PROTECTED_OBJECT_INPUT_INVALID,
                    "protected object input is invalid");
        }

        public static Failure denied() {
            return new Failure(Category.PROTECTED_OBJECT_ACCESS_DENIED,
                    "protected object access denied");
        }

        public static Failure integrity() {
            return new Failure(Category.PROTECTED_OBJECT_INTEGRITY_INVALID,
                    "protected object integrity check failed");
        }

        public static Failure unavailable() {
            return new Failure(Category.PROTECTED_OBJECT_UNAVAILABLE,
                    "protected object service is unavailable");
        }
    }
}
