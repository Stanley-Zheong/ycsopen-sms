package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact SQL owner for safe protected-object metadata, operation state, and capability digests.
 *
 * <p>No method returns a bucket, URL, capability token, ciphertext, or provider diagnostic. The
 * opaque store locator is visible only to the object service and is redacted from value rendering.</p>
 */
@Repository
public class ProtectedObjectMetadataRepository implements ObjectCapabilityService.CapabilityStore {

    private static final String CAPABILITY_DIGEST_PURPOSE = "OBJECT_CAPABILITY_DIGEST";

    private final Store store;

    public ProtectedObjectMetadataRepository(JdbcTemplate jdbcTemplate,
                                             PlatformTransactionManager transactionManager) {
        this(new JdbcStore(jdbcTemplate, transactionManager));
    }

    ProtectedObjectMetadataRepository(Store store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public void beginCreate(CreateOperation operation) {
        require(operation);
        store.beginCreate(operation);
    }

    public Optional<ProtectedObjectMetadata> completeCreate(CreateOperation operation,
                                                             StoredObjectMetadata stored) {
        require(operation);
        Objects.requireNonNull(stored, "stored");
        if (stored.purpose() != operation.purpose()) {
            throw new IllegalArgumentException("protected object metadata is invalid");
        }
        return store.completeCreate(operation, stored);
    }

    public void recordOrphan(CreateOperation operation, StoredObjectMetadata stored) {
        require(operation);
        Objects.requireNonNull(stored, "stored");
        if (stored.purpose() != operation.purpose()) {
            throw new IllegalArgumentException("protected object metadata is invalid");
        }
        store.recordOrphan(operation, stored);
    }

    public void failCreate(String operationId) {
        requireOperationId(operationId);
        store.failCreate(operationId);
    }

    public Optional<ProtectedObjectMetadata> find(String protectedObjectId) {
        requireObjectId(protectedObjectId);
        return store.find(protectedObjectId);
    }

    public void markDeleted(String protectedObjectId) {
        requireObjectId(protectedObjectId);
        store.markDeleted(protectedObjectId);
    }

    public void markOrphaned(String protectedObjectId) {
        requireObjectId(protectedObjectId);
        store.markOrphaned(protectedObjectId);
    }

    public List<ProtectedObjectMetadata> reconciliationCandidates(int limit) {
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("reconciliation limit is invalid");
        }
        return List.copyOf(store.reconciliationCandidates(limit));
    }

    @Override
    public boolean create(ObjectCapabilityService.StoredCapability capability) {
        return store.createCapability(Objects.requireNonNull(capability, "capability"));
    }

    @Override
    public Optional<ObjectCapabilityService.StoredCapability> findByLookupId(String lookupId) {
        if (lookupId == null || lookupId.isBlank()) {
            return Optional.empty();
        }
        return store.findCapability(lookupId);
    }

    private static void require(CreateOperation operation) {
        Objects.requireNonNull(operation, "operation");
    }

    private static void requireOperationId(String operationId) {
        if (operationId == null || !operationId.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("operation id is invalid");
        }
    }

    private static void requireObjectId(String protectedObjectId) {
        if (protectedObjectId == null || !protectedObjectId.matches("pobj_v1_[A-Za-z0-9_-]{32}")) {
            throw new IllegalArgumentException("protected object id is invalid");
        }
    }

    public record CreateOperation(String operationId,
                                  String protectedObjectId,
                                  String registrationSessionId,
                                  String tenantDraftId,
                                  PrivateObjectStorePort.ObjectPurpose purpose,
                                  int attemptNumber,
                                  Instant expiresAt,
                                  String replacesObjectId) {
        public CreateOperation {
            requireOperationId(operationId);
            requireObjectId(protectedObjectId);
            if (registrationSessionId == null || !registrationSessionId.matches(
                    "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                    || tenantDraftId == null || !tenantDraftId.matches(
                    "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                    || purpose == null || attemptNumber < 1 || attemptNumber > 3
                    || expiresAt == null) {
                throw new IllegalArgumentException("protected object operation is invalid");
            }
            if (replacesObjectId != null) {
                requireObjectId(replacesObjectId);
                if (replacesObjectId.equals(protectedObjectId)) {
                    throw new IllegalArgumentException("protected object operation is invalid");
                }
            }
        }

        @Override
        public String toString() {
            return "CreateOperation[operationId=" + operationId + ", protectedObjectId="
                    + protectedObjectId + ", binding=[redacted], purpose=" + purpose
                    + ", attemptNumber=" + attemptNumber + ", expiresAt=" + expiresAt
                    + ", replaces=" + (replacesObjectId == null ? "none" : "[redacted]") + "]";
        }
    }

    public static final class ProtectedObjectMetadata {
        private final String protectedObjectId;
        private final String registrationSessionId;
        private final String tenantDraftId;
        private final PrivateObjectStorePort.ObjectPurpose purpose;
        private final ObjectState state;
        private final String storageKey;
        private final String envelopeSha256;
        private final long envelopeSize;
        private final String mediaType;
        private final Instant expiresAt;

        public ProtectedObjectMetadata(String protectedObjectId,
                                       String registrationSessionId,
                                       String tenantDraftId,
                                       PrivateObjectStorePort.ObjectPurpose purpose,
                                       ObjectState state,
                                       String storageKey,
                                       String envelopeSha256,
                                       long envelopeSize,
                                       String mediaType,
                                       Instant expiresAt) {
            requireObjectId(protectedObjectId);
            this.protectedObjectId = protectedObjectId;
            this.registrationSessionId = Objects.requireNonNull(registrationSessionId, "registrationSessionId");
            this.tenantDraftId = Objects.requireNonNull(tenantDraftId, "tenantDraftId");
            this.purpose = Objects.requireNonNull(purpose, "purpose");
            this.state = Objects.requireNonNull(state, "state");
            this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
            this.envelopeSha256 = Objects.requireNonNull(envelopeSha256, "envelopeSha256");
            this.envelopeSize = envelopeSize;
            this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            if (envelopeSize < 1 || envelopeSize > purpose.maximumEnvelopeBytes()) {
                throw new IllegalArgumentException("protected object metadata is invalid");
            }
        }

        public String protectedObjectId() {
            return protectedObjectId;
        }

        public String registrationSessionId() {
            return registrationSessionId;
        }

        public String tenantDraftId() {
            return tenantDraftId;
        }

        public PrivateObjectStorePort.ObjectPurpose purpose() {
            return purpose;
        }

        public ObjectState state() {
            return state;
        }

        String storageKey() {
            return storageKey;
        }

        String envelopeSha256() {
            return envelopeSha256;
        }

        public long envelopeSize() {
            return envelopeSize;
        }

        public String mediaType() {
            return mediaType;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        @Override
        public String toString() {
            return "ProtectedObjectMetadata[protectedObjectId=" + protectedObjectId
                    + ", binding=[redacted], purpose=" + purpose + ", state=" + state
                    + ", storageKey=[redacted], envelopeSha256=[redacted], envelopeSize="
                    + envelopeSize + ", mediaType=" + mediaType + ", expiresAt=" + expiresAt + "]";
        }
    }

    public enum ObjectState {
        STAGED,
        CLAIMED,
        REPLACED,
        EXPIRED,
        ORPHANED,
        DELETED
    }

    interface Store {
        void beginCreate(CreateOperation operation);

        Optional<ProtectedObjectMetadata> completeCreate(CreateOperation operation,
                                                         StoredObjectMetadata stored);

        void recordOrphan(CreateOperation operation, StoredObjectMetadata stored);

        void failCreate(String operationId);

        Optional<ProtectedObjectMetadata> find(String protectedObjectId);

        void markDeleted(String protectedObjectId);

        void markOrphaned(String protectedObjectId);

        List<ProtectedObjectMetadata> reconciliationCandidates(int limit);

        boolean createCapability(ObjectCapabilityService.StoredCapability capability);

        Optional<ObjectCapabilityService.StoredCapability> findCapability(String lookupId);
    }

    private static final class JdbcStore implements Store {
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transaction;

        private JdbcStore(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
            this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
            this.transaction = new TransactionTemplate(
                    Objects.requireNonNull(transactionManager, "transactionManager"));
        }

        @Override
        public void beginCreate(CreateOperation operation) {
            int inserted = jdbc.update("""
                    INSERT INTO ycs_crypto_object_operations
                        (operation_id, registration_session_id, object_purpose,
                         operation_state, attempt_number)
                    VALUES (?, ?, ?, 'RESERVED', ?)
                    """, operation.operationId(), operation.registrationSessionId(),
                    databasePurpose(operation.purpose()), operation.attemptNumber());
            requireOne(inserted);
        }

        @Override
        public Optional<ProtectedObjectMetadata> completeCreate(CreateOperation operation,
                                                                 StoredObjectMetadata stored) {
            return transaction.execute(status -> {
                Optional<ProtectedObjectMetadata> replaced = Optional.empty();
                if (operation.replacesObjectId() != null) {
                    replaced = find(operation.replacesObjectId());
                    int updated = jdbc.update("""
                            UPDATE ycs_crypto_protected_objects
                               SET object_state = 'REPLACED', optimistic_version = optimistic_version + 1
                             WHERE protected_object_id = ?
                               AND registration_session_id = ?
                               AND tenant_draft_id = ?
                               AND object_purpose = ?
                               AND object_state = 'STAGED'
                            """, operation.replacesObjectId(), operation.registrationSessionId(),
                            operation.tenantDraftId(), databasePurpose(operation.purpose()));
                    requireOne(updated);
                }
                insertObject(operation, stored, "STAGED");
                int updated = jdbc.update("""
                        UPDATE ycs_crypto_object_operations
                           SET protected_object_id = ?, operation_state = 'COMPLETED',
                               affected_count = 1, optimistic_version = optimistic_version + 1
                         WHERE operation_id = ? AND operation_state = 'RESERVED'
                        """, operation.protectedObjectId(), operation.operationId());
                requireOne(updated);
                return replaced;
            });
        }

        @Override
        public void recordOrphan(CreateOperation operation, StoredObjectMetadata stored) {
            transaction.executeWithoutResult(status -> {
                insertObject(operation, stored, "ORPHANED");
                int updated = jdbc.update("""
                        UPDATE ycs_crypto_object_operations
                           SET protected_object_id = ?, operation_state = 'RECONCILE_DELETE',
                               optimistic_version = optimistic_version + 1
                         WHERE operation_id = ? AND operation_state IN ('RESERVED', 'OBJECT_STORED')
                        """, operation.protectedObjectId(), operation.operationId());
                requireOne(updated);
            });
        }

        private void insertObject(CreateOperation operation, StoredObjectMetadata stored, String state) {
            int inserted = jdbc.update("""
                    INSERT INTO ycs_crypto_protected_objects
                        (protected_object_id, registration_session_id, tenant_draft_id,
                         object_purpose, object_state, opaque_store_locator,
                         envelope_digest, envelope_size, media_type, replaces_object_id, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, UNHEX(?), ?, ?, ?, ?)
                    """, operation.protectedObjectId(), operation.registrationSessionId(),
                    operation.tenantDraftId(), databasePurpose(operation.purpose()), state,
                    stored.storageKey(), stored.sha256(), stored.size(), stored.mediaType(),
                    operation.replacesObjectId(), Timestamp.from(operation.expiresAt()));
            requireOne(inserted);
        }

        @Override
        public void failCreate(String operationId) {
            jdbc.update("""
                    UPDATE ycs_crypto_object_operations
                       SET operation_state = 'FAILED', optimistic_version = optimistic_version + 1
                     WHERE operation_id = ? AND operation_state = 'RESERVED'
                    """, operationId);
        }

        @Override
        public Optional<ProtectedObjectMetadata> find(String protectedObjectId) {
            return jdbc.query("""
                    SELECT protected_object_id, registration_session_id, tenant_draft_id,
                           object_purpose, object_state, opaque_store_locator,
                           LOWER(HEX(envelope_digest)) AS envelope_sha256,
                           envelope_size, media_type, expires_at
                      FROM ycs_crypto_protected_objects
                     WHERE protected_object_id = ?
                    """, (rs, row) -> new ProtectedObjectMetadata(
                    rs.getString("protected_object_id"),
                    rs.getString("registration_session_id"),
                    rs.getString("tenant_draft_id"),
                    objectPurpose(rs.getString("object_purpose")),
                    ObjectState.valueOf(rs.getString("object_state")),
                    rs.getString("opaque_store_locator"),
                    rs.getString("envelope_sha256"),
                    rs.getLong("envelope_size"),
                    rs.getString("media_type"),
                    rs.getTimestamp("expires_at").toInstant()), protectedObjectId)
                    .stream().findFirst();
        }

        @Override
        public void markDeleted(String protectedObjectId) {
            int updated = jdbc.update("""
                    UPDATE ycs_crypto_protected_objects
                       SET object_state = 'DELETED', optimistic_version = optimistic_version + 1
                     WHERE protected_object_id = ?
                       AND object_state IN ('STAGED', 'REPLACED', 'EXPIRED', 'ORPHANED')
                    """, protectedObjectId);
            requireOne(updated);
            jdbc.update("""
                    UPDATE ycs_crypto_object_operations
                       SET operation_state = 'COMPLETED', affected_count = 1,
                           optimistic_version = optimistic_version + 1
                     WHERE protected_object_id = ? AND operation_state = 'RECONCILE_DELETE'
                    """, protectedObjectId);
        }

        @Override
        public void markOrphaned(String protectedObjectId) {
            int updated = jdbc.update("""
                    UPDATE ycs_crypto_protected_objects
                       SET object_state = 'ORPHANED', optimistic_version = optimistic_version + 1
                     WHERE protected_object_id = ?
                       AND object_state IN ('STAGED', 'REPLACED', 'EXPIRED')
                    """, protectedObjectId);
            requireOne(updated);
            jdbc.update("""
                    UPDATE ycs_crypto_object_operations
                       SET operation_state = 'RECONCILE_DELETE',
                           optimistic_version = optimistic_version + 1
                     WHERE protected_object_id = ? AND operation_state = 'COMPLETED'
                    """, protectedObjectId);
        }

        @Override
        public List<ProtectedObjectMetadata> reconciliationCandidates(int limit) {
            return jdbc.query("""
                    SELECT protected_object_id, registration_session_id, tenant_draft_id,
                           object_purpose, object_state, opaque_store_locator,
                           LOWER(HEX(envelope_digest)) AS envelope_sha256,
                           envelope_size, media_type, expires_at
                      FROM ycs_crypto_protected_objects
                     WHERE object_state IN ('REPLACED', 'EXPIRED', 'ORPHANED')
                     ORDER BY updated_at, protected_object_id
                     LIMIT ?
                    """, (rs, row) -> new ProtectedObjectMetadata(
                    rs.getString("protected_object_id"),
                    rs.getString("registration_session_id"),
                    rs.getString("tenant_draft_id"),
                    objectPurpose(rs.getString("object_purpose")),
                    ObjectState.valueOf(rs.getString("object_state")),
                    rs.getString("opaque_store_locator"),
                    rs.getString("envelope_sha256"),
                    rs.getLong("envelope_size"),
                    rs.getString("media_type"),
                    rs.getTimestamp("expires_at").toInstant()), limit);
        }

        @Override
        public boolean createCapability(ObjectCapabilityService.StoredCapability capability) {
            try {
                VersionedTokenDigest digest = capability.credentialDigest();
                int inserted = jdbc.update("""
                        INSERT INTO ycs_crypto_object_capabilities
                            (capability_lookup_id, protected_object_id, tenant_binding_digest,
                             subject_binding_digest, capability_purpose, digest_key_purpose,
                             digest_key_version, capability_credential_digest,
                             capability_state, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, capability.lookupId(), capability.protectedObjectId(),
                        capability.tenantBindingDigest(), capability.subjectBindingDigest(),
                        capability.purpose(), CAPABILITY_DIGEST_PURPOSE, digest.keyVersion(),
                        digest.digest(), capability.state().name(), Timestamp.from(capability.expiresAt()));
                requireOne(inserted);
                return true;
            } catch (DuplicateKeyException duplicate) {
                return false;
            }
        }

        @Override
        public Optional<ObjectCapabilityService.StoredCapability> findCapability(String lookupId) {
            return jdbc.query("""
                    SELECT capability_lookup_id, protected_object_id, tenant_binding_digest,
                           subject_binding_digest, capability_purpose, digest_key_version,
                           capability_credential_digest, capability_state, expires_at
                      FROM ycs_crypto_object_capabilities
                     WHERE capability_lookup_id = ?
                    """, (rs, row) -> new ObjectCapabilityService.StoredCapability(
                    rs.getString("capability_lookup_id"),
                    rs.getString("protected_object_id"),
                    rs.getBytes("tenant_binding_digest"),
                    rs.getBytes("subject_binding_digest"),
                    rs.getString("capability_purpose"),
                    new VersionedTokenDigest(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                            rs.getLong("digest_key_version"),
                            rs.getBytes("capability_credential_digest")),
                    ObjectAccessAuthorizationPort.CapabilityState.valueOf(
                            rs.getString("capability_state")),
                    rs.getTimestamp("expires_at").toInstant()), lookupId)
                    .stream().findFirst();
        }

        private static void requireOne(int affected) {
            if (affected != 1) {
                throw new IllegalStateException("protected object metadata operation failed");
            }
        }

        private static String databasePurpose(PrivateObjectStorePort.ObjectPurpose purpose) {
            return switch (purpose) {
                case BUSINESS_LICENSE -> "BUSINESS_LICENSE";
                case REPRESENTATIVE_ID_FRONT -> "LEGAL_REPRESENTATIVE_ID_FRONT";
                case REPRESENTATIVE_ID_BACK -> "LEGAL_REPRESENTATIVE_ID_BACK";
                case SHORT_LINK_DOMAIN_PROOF -> "SHORT_LINK_PROOF";
                case TRADEMARK_PROOF -> "TRADEMARK_PROOF";
            };
        }

        private static PrivateObjectStorePort.ObjectPurpose objectPurpose(String databaseValue) {
            return switch (databaseValue) {
                case "BUSINESS_LICENSE" -> PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE;
                case "LEGAL_REPRESENTATIVE_ID_FRONT" ->
                        PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_FRONT;
                case "LEGAL_REPRESENTATIVE_ID_BACK" ->
                        PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_BACK;
                case "SHORT_LINK_PROOF" ->
                        PrivateObjectStorePort.ObjectPurpose.SHORT_LINK_DOMAIN_PROOF;
                case "TRADEMARK_PROOF" -> PrivateObjectStorePort.ObjectPurpose.TRADEMARK_PROOF;
                default -> throw new IllegalStateException("protected object metadata operation failed");
            };
        }
    }
}
