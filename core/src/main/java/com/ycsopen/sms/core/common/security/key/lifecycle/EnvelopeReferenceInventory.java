package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotChunkStore;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotManifest;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Produces a deterministic, non-secret proof of every live key reference. */
public final class EnvelopeReferenceInventory {

    static final String DATABASE_FIELD_SOURCE = "DATABASE_FIELD_ENVELOPES";
    static final String OBJECT_RESERVATION_SOURCE = "OBJECT_FIELD_RESERVATIONS";
    static final String SNAPSHOT_SOURCE = "SNAPSHOT_ENVELOPES";

    private static final List<DatabaseFieldTarget> DATABASE_FIELD_TARGETS = List.of(
            target("users.phone_encrypted", "users", "phone_encrypted", "id"),
            target("tenants.legal_rep_id_no_encrypted", "tenants", "legal_rep_id_no_encrypted", "id"),
            target("tenants.contact_id_no_encrypted", "tenants", "contact_id_no_encrypted", "id"),
            target("tenants.contact_phone_encrypted", "tenants", "contact_phone_encrypted", "id"),
            target("signatures.applicant_phone_encrypted", "signatures", "applicant_phone_encrypted", "id"),
            target("signatures.applicant_id_no_encrypted", "signatures", "applicant_id_no_encrypted", "id"),
            target("channels.account_encrypted", "channels", "account_encrypted", "id"),
            target("channels.password_encrypted", "channels", "password_encrypted", "id"),
            target("mobile_portability.mobile_encrypted", "mobile_portability", "mobile_encrypted", "mobile_hash"),
            target("blacklist_entries.mobile_encrypted", "blacklist_entries", "mobile_encrypted", "id"),
            target("tenant_api_keys.app_secret_encrypted", "tenant_api_keys", "app_secret_encrypted", "id"),
            target("tenant_protocol_credentials.account_encrypted", "tenant_protocol_credentials", "account_encrypted", "id"),
            target("tenant_protocol_credentials.password_encrypted", "tenant_protocol_credentials", "password_encrypted", "id"),
            target("message_tasks.mobile_encrypted", "message_tasks", "mobile_encrypted", "message_id"),
            target("bulk_sending_items.mobile_encrypted", "bulk_sending_items", "mobile_encrypted", "id"),
            target("uplink_records.mobile_encrypted", "uplink_records", "mobile_encrypted", "id"),
            target("unsubscribe_records.mobile_encrypted", "unsubscribe_records", "mobile_encrypted", "id")
    );

    public enum Kind {
        DATABASE_ENVELOPE,
        PROTECTED_OBJECT,
        BLIND_INDEX,
        OBJECT_CAPABILITY,
        REGISTRATION_UPLOAD_SESSION,
        SNAPSHOT_ENVELOPE
    }

    /** A locator digest identifies the row/object without exposing its locator. */
    public static final class Reference {
        private final String source;
        private final Kind kind;
        private final KeyReferenceRepository.Purpose purpose;
        private final long keyVersion;
        private final byte[] locatorDigest;

        public Reference(String source,
                         Kind kind,
                         KeyReferenceRepository.Purpose purpose,
                         long keyVersion,
                         byte[] locatorDigest) {
            if (!validSource(source) || kind == null || purpose == null || keyVersion < 1
                    || locatorDigest == null || locatorDigest.length != 32) {
                throw new IllegalArgumentException("invalid key reference inventory item");
            }
            this.source = source;
            this.kind = kind;
            this.purpose = purpose;
            this.keyVersion = keyVersion;
            this.locatorDigest = locatorDigest.clone();
        }

        public String source() {
            return source;
        }

        public Kind kind() {
            return kind;
        }

        public KeyReferenceRepository.Purpose purpose() {
            return purpose;
        }

        public long keyVersion() {
            return keyVersion;
        }

        public byte[] locatorDigest() {
            return locatorDigest.clone();
        }

        String canonicalIdentity() {
            return source + '\0' + kind + '\0' + purpose + '\0' + keyVersion + '\0'
                    + HexFormat.of().formatHex(locatorDigest);
        }

        @Override
        public String toString() {
            return "Reference[source=" + source + ", kind=" + kind + ", purpose=" + purpose
                    + ", keyVersion=" + keyVersion + ", locator=[redacted]]";
        }
    }

    public interface Source {
        String sourceId();

        List<Reference> liveReferences();

        /** Allows an unavailable purpose-owned store to block only its own retirement. */
        default boolean supports(KeyReferenceRepository.Purpose purpose) {
            return true;
        }
    }

    public static final class Snapshot {
        private final Map<KeyVersion, Long> counts;
        private final byte[] digest;

        private Snapshot(Map<KeyVersion, Long> counts, byte[] digest) {
            this.counts = Map.copyOf(counts);
            this.digest = digest.clone();
        }

        public long count(KeyReferenceRepository.Purpose purpose, long keyVersion) {
            return counts.getOrDefault(new KeyVersion(purpose, keyVersion), 0L);
        }

        public Map<KeyVersion, Long> counts() {
            return counts;
        }

        public byte[] digest() {
            return digest.clone();
        }

        @Override
        public String toString() {
            return "Snapshot[keyVersions=" + counts.size() + ", digest=[redacted]]";
        }
    }

    public record KeyVersion(KeyReferenceRepository.Purpose purpose, long keyVersion) {
        public KeyVersion {
            Objects.requireNonNull(purpose, "purpose");
            if (keyVersion < 1) {
                throw new IllegalArgumentException("invalid key version");
            }
        }
    }

    private static final Pattern SOURCE_ID = Pattern.compile("[A-Z0-9][A-Z0-9_.-]{0,95}");
    private final Set<String> requiredSources;
    private final List<Source> sources;

    public EnvelopeReferenceInventory(Set<String> requiredSources, List<Source> sources) {
        this.requiredSources = Set.copyOf(Objects.requireNonNull(requiredSources, "requiredSources"));
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Set<String> supplied = new HashSet<>();
        for (Source source : this.sources) {
            Objects.requireNonNull(source, "source");
            if (!validSource(source.sourceId()) || !supplied.add(source.sourceId())) {
                throw new IllegalArgumentException("invalid reference inventory source set");
            }
        }
        if (!supplied.equals(this.requiredSources)) {
            throw new IllegalArgumentException("incomplete reference inventory source set");
        }
    }

    public Snapshot snapshot() {
        return snapshot(null);
    }

    Snapshot snapshot(KeyReferenceRepository.Purpose purpose) {
        List<Reference> references = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (Source source : sources) {
            if (purpose != null && !source.supports(purpose)) {
                continue;
            }
            List<Reference> supplied = List.copyOf(Objects.requireNonNull(
                    source.liveReferences(), "live references"));
            for (Reference reference : supplied) {
                if (reference == null || !source.sourceId().equals(reference.source())
                        || !unique.add(reference.canonicalIdentity())) {
                    throw new IllegalStateException("key reference inventory invariant failed");
                }
                references.add(reference);
            }
        }
        references.sort(Comparator.comparing(Reference::canonicalIdentity));
        Map<KeyVersion, Long> counts = new LinkedHashMap<>();
        for (Reference reference : references) {
            counts.merge(new KeyVersion(reference.purpose(), reference.keyVersion()), 1L, Long::sum);
        }
        return new Snapshot(counts, digest(references));
    }

    boolean containsSource(String sourceId) {
        return requiredSources.contains(sourceId);
    }

    /** Metadata-table sources; database/object-envelope sources must be supplied by their owners. */
    public static List<Source> jdbcMetadataSources(JdbcTemplate jdbc) {
        Objects.requireNonNull(jdbc, "jdbc");
        return List.of(
                jdbcSource(DATABASE_FIELD_SOURCE, () -> databaseFieldReferences(jdbc)),
                jdbcSource(OBJECT_RESERVATION_SOURCE, () -> objectFieldReservations(jdbc)),
                jdbcSource("BLIND_INDEX_METADATA", () -> jdbc.query("""
                        SELECT target_type, legacy_row_id, field_id, key_version,
                               original_row_digest
                          FROM ycs_crypto_blind_indexes
                         WHERE key_purpose = 'MOBILE_BLIND_INDEX'
                           AND index_status IN ('ACTIVE', 'RETIRING')
                         ORDER BY target_type, legacy_row_id, field_id, key_version
                        """, (rs, row) -> new Reference("BLIND_INDEX_METADATA", Kind.BLIND_INDEX,
                        KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX,
                        rs.getLong("key_version"), rs.getBytes("original_row_digest")))),
                jdbcSource("OBJECT_CAPABILITY_METADATA", () -> jdbc.query("""
                        SELECT digest_key_version, capability_lookup_id
                          FROM ycs_crypto_object_capabilities
                         WHERE digest_key_purpose = 'OBJECT_CAPABILITY_DIGEST'
                           AND capability_state = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP(6)
                         ORDER BY capability_lookup_id
                        """, (rs, row) -> new Reference("OBJECT_CAPABILITY_METADATA",
                        Kind.OBJECT_CAPABILITY,
                        KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST,
                        rs.getLong("digest_key_version"), sha256Ascii(rs.getString("capability_lookup_id"))))),
                jdbcSource("REGISTRATION_SESSION_METADATA", () -> jdbc.query("""
                        SELECT upload_digest_key_version, registration_session_id
                          FROM ycs_crypto_registration_sessions
                         WHERE upload_digest_purpose = 'REGISTRATION_UPLOAD_DIGEST'
                           AND session_state = 'OPEN' AND expires_at > CURRENT_TIMESTAMP(6)
                         ORDER BY registration_session_id
                        """, (rs, row) -> new Reference("REGISTRATION_SESSION_METADATA",
                        Kind.REGISTRATION_UPLOAD_SESSION,
                        KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST,
                        rs.getLong("upload_digest_key_version"),
                        sha256Ascii(rs.getString("registration_session_id")))))
        );
    }

    /** Canonical retained-snapshot inventory. Missing/unreadable stores fail closed on use. */
    public static Source snapshotEnvelopeSource(JdbcTemplate jdbc, SnapshotChunkStore store) {
        Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(store, "store");
        return scopedSource(SNAPSHOT_SOURCE, KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY,
                () -> snapshotReferences(jdbc, store));
    }

    /** Production placeholder when no snapshot root is configured; SNAPSHOT retirement rejects. */
    public static Source unavailableSnapshotEnvelopeSource() {
        return scopedSource(SNAPSHOT_SOURCE, KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY,
                () -> {
                    throw new IllegalStateException("key reference inventory invariant failed");
                });
    }

    private static List<Reference> snapshotReferences(
            JdbcTemplate jdbc, SnapshotChunkStore store) {
        EnvelopeCodec codec = new EnvelopeCodec();
        Map<FieldKeyIdentity, Long> knownKeys = new LinkedHashMap<>();
        jdbc.query("""
                SELECT key_version, provider_id, provider_key_reference
                  FROM ycs_crypto_key_references
                 WHERE purpose = 'SNAPSHOT_RECOVERY'
                 ORDER BY key_version
                """, resultSet -> {
            FieldKeyIdentity identity = new FieldKeyIdentity(
                    resultSet.getString("provider_id"),
                    resultSet.getString("provider_key_reference"));
            if (knownKeys.put(identity, resultSet.getLong("key_version")) != null) {
                throw new IllegalStateException("key reference inventory invariant failed");
            }
        });
        List<Reference> references = new ArrayList<>();
        for (SnapshotChunkStore.RetainedManifest retained : store.retainedManifests()) {
            SnapshotManifest manifest = SnapshotManifest.parse(retained.canonicalManifest());
            if (!retained.snapshotId().equals(manifest.snapshotId())) {
                throw new IllegalStateException("key reference inventory invariant failed");
            }
            List<SnapshotChunkStore.StoredChunk> actual = store.inventory(manifest.snapshotId());
            if (actual.size() != manifest.chunks().size()) {
                throw new IllegalStateException("key reference inventory invariant failed");
            }
            for (int index = 0; index < manifest.chunks().size(); index++) {
                SnapshotManifest.Chunk expected = manifest.chunks().get(index);
                SnapshotChunkStore.StoredChunk stored = actual.get(index);
                if (stored.index() != expected.index()
                        || stored.envelopeSize() != expected.envelopeSize()) {
                    throw new IllegalStateException("key reference inventory invariant failed");
                }
                byte[] encoded = readSnapshotEnvelope(store, manifest.snapshotId(), expected);
                try {
                    if (!sha256Hex(encoded).equals(expected.sha256Digest())) {
                        throw new IllegalStateException("key reference inventory invariant failed");
                    }
                    CipherEnvelope envelope = codec.decode(
                            encoded, EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
                    if (!manifest.recoveryKeyReference().equals(envelope.keyReference())) {
                        throw new IllegalStateException("key reference inventory invariant failed");
                    }
                    Long version = knownKeys.get(new FieldKeyIdentity(
                            envelope.providerId(), envelope.keyReference()));
                    if (version == null) {
                        throw new IllegalStateException("key reference inventory invariant failed");
                    }
                    references.add(new Reference(SNAPSHOT_SOURCE, Kind.SNAPSHOT_ENVELOPE,
                            KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, version,
                            sha256Ascii(manifest.snapshotId() + ":" + expected.index())));
                } catch (RuntimeException failure) {
                    throw new IllegalStateException("key reference inventory invariant failed");
                } finally {
                    Arrays.fill(encoded, (byte) 0);
                }
            }
        }
        return List.copyOf(references);
    }

    private static byte[] readSnapshotEnvelope(
            SnapshotChunkStore store, String snapshotId, SnapshotManifest.Chunk chunk) {
        int expected = Math.toIntExact(chunk.envelopeSize());
        byte[] value = new byte[expected];
        try (InputStream input = store.open(snapshotId, chunk.index(), chunk.envelopeSize())) {
            int offset = 0;
            while (offset < value.length) {
                int read = input.read(value, offset, value.length - offset);
                if (read < 0) {
                    throw new IllegalStateException("key reference inventory invariant failed");
                }
                offset += read;
            }
            if (input.read() != -1) {
                throw new IllegalStateException("key reference inventory invariant failed");
            }
            return value;
        } catch (IOException | RuntimeException failure) {
            Arrays.fill(value, (byte) 0);
            throw new IllegalStateException("key reference inventory invariant failed");
        }
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("reference inventory digest unavailable", impossible);
        }
    }

    private static Source scopedSource(
            String sourceId,
            KeyReferenceRepository.Purpose purpose,
            java.util.function.Supplier<List<Reference>> supplier) {
        return new Source() {
            @Override
            public String sourceId() {
                return sourceId;
            }

            @Override
            public List<Reference> liveReferences() {
                return supplier.get();
            }

            @Override
            public boolean supports(KeyReferenceRepository.Purpose requested) {
                return purpose == requested;
            }
        };
    }

    static Set<String> databaseFieldTargetIds() {
        return DATABASE_FIELD_TARGETS.stream().map(DatabaseFieldTarget::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<Reference> databaseFieldReferences(JdbcTemplate jdbc) {
        EnvelopeCodec codec = new EnvelopeCodec();
        Map<FieldKeyIdentity, Long> knownKeys = new LinkedHashMap<>();
        jdbc.query("""
                SELECT key_version, provider_id, provider_key_reference
                  FROM ycs_crypto_key_references
                 WHERE purpose = 'FIELD_ENCRYPTION_KEK'
                 ORDER BY key_version
                """, resultSet -> {
            FieldKeyIdentity identity = new FieldKeyIdentity(
                    resultSet.getString("provider_id"),
                    resultSet.getString("provider_key_reference"));
            if (knownKeys.put(identity, resultSet.getLong("key_version")) != null) {
                throw new IllegalStateException("key reference inventory invariant failed");
            }
        });
        List<Reference> references = new ArrayList<>();
        for (DatabaseFieldTarget target : DATABASE_FIELD_TARGETS) {
            String sql = "SELECT CAST(`" + target.identityColumn() + "` AS BINARY) AS row_identity, `"
                    + target.column() + "` AS encoded FROM `" + target.table() + "` WHERE `"
                    + target.column() + "` IS NOT NULL ORDER BY `" + target.identityColumn() + "`";
            jdbc.query(sql, resultSet -> {
                byte[] encoded = resultSet.getBytes("encoded");
                byte[] rowIdentity = resultSet.getBytes("row_identity");
                try {
                    if (encoded == null || rowIdentity == null) {
                        throw new IllegalStateException("key reference inventory invariant failed");
                    }
                    CipherEnvelope envelope = codec.decode(encoded, EnvelopeCodec.Target.DATABASE_FIELD);
                    Long version = knownKeys.get(new FieldKeyIdentity(
                            envelope.providerId(), envelope.keyReference()));
                    if (version == null) {
                        throw new IllegalStateException("key reference inventory invariant failed");
                    }
                    references.add(new Reference(DATABASE_FIELD_SOURCE, Kind.DATABASE_ENVELOPE,
                            KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, version,
                            databaseLocatorDigest(target.id(), rowIdentity)));
                } catch (RuntimeException invalid) {
                    throw new IllegalStateException("key reference inventory invariant failed");
                } finally {
                    if (encoded != null) {
                        Arrays.fill(encoded, (byte) 0);
                    }
                    if (rowIdentity != null) {
                        Arrays.fill(rowIdentity, (byte) 0);
                    }
                }
            });
        }
        return List.copyOf(references);
    }

    private static byte[] databaseLocatorDigest(String targetId, byte[] rowIdentity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(targetId.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(rowIdentity);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("reference inventory digest unavailable", impossible);
        }
    }

    private static DatabaseFieldTarget target(
            String id, String table, String column, String identityColumn) {
        return new DatabaseFieldTarget(id, table, column, identityColumn);
    }

    private static List<Reference> objectFieldReservations(JdbcTemplate jdbc) {
        Integer incompleteOperations = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM ycs_crypto_object_operations operation_row
                  LEFT JOIN ycs_crypto_protected_objects object_row
                    ON object_row.protected_object_id = operation_row.protected_object_id
                 WHERE operation_row.field_key_version IS NULL
                   AND (operation_row.operation_state IN
                           ('RESERVED', 'OBJECT_STORED', 'METADATA_COMMITTED',
                            'RECONCILE_DELETE')
                        OR (operation_row.operation_state = 'COMPLETED'
                            AND (object_row.protected_object_id IS NULL
                                 OR object_row.object_state <> 'DELETED')))
                """, Integer.class);
        Integer incompleteObjects = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM (
                        SELECT object_row.protected_object_id
                          FROM ycs_crypto_protected_objects object_row
                          LEFT JOIN ycs_crypto_object_operations operation_row
                            ON operation_row.protected_object_id = object_row.protected_object_id
                           AND operation_row.field_key_purpose = 'FIELD_ENCRYPTION_KEK'
                           AND operation_row.field_key_version IS NOT NULL
                         WHERE object_row.object_state <> 'DELETED'
                         GROUP BY object_row.protected_object_id
                        HAVING COUNT(operation_row.operation_id) <> 1
                       ) incomplete
                """, Integer.class);
        if (incompleteOperations == null || incompleteOperations != 0
                || incompleteObjects == null || incompleteObjects != 0) {
            throw new IllegalStateException("key reference inventory invariant failed");
        }
        return jdbc.query("""
                SELECT operation_id, field_key_version
                  FROM ycs_crypto_object_operations
                 WHERE field_key_purpose = 'FIELD_ENCRYPTION_KEK'
                   AND field_key_version IS NOT NULL
                 ORDER BY operation_id
                """, (rs, row) -> new Reference("OBJECT_FIELD_RESERVATIONS",
                Kind.PROTECTED_OBJECT, KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK,
                rs.getLong("field_key_version"), sha256Ascii(rs.getString("operation_id"))));
    }

    private static Source jdbcSource(String sourceId, ReferenceQuery query) {
        return new Source() {
            @Override
            public String sourceId() {
                return sourceId;
            }

            @Override
            public List<Reference> liveReferences() {
                return query.read();
            }
        };
    }

    private static byte[] digest(List<Reference> references) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Reference reference : references) {
                byte[] canonical = reference.canonicalIdentity().getBytes(StandardCharsets.US_ASCII);
                digest.update((byte) (canonical.length >>> 24));
                digest.update((byte) (canonical.length >>> 16));
                digest.update((byte) (canonical.length >>> 8));
                digest.update((byte) canonical.length);
                digest.update(canonical);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("reference inventory digest unavailable");
        }
    }

    private static byte[] sha256Ascii(String value) {
        if (value == null) {
            throw new IllegalStateException("key reference inventory invariant failed");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("reference inventory digest unavailable");
        }
    }

    private static boolean validSource(String source) {
        return source != null && SOURCE_ID.matcher(source).matches();
    }

    @FunctionalInterface
    private interface ReferenceQuery {
        List<Reference> read();
    }

    private record FieldKeyIdentity(String providerId, String keyReference) {
        private FieldKeyIdentity {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(keyReference, "keyReference");
        }
    }

    private record DatabaseFieldTarget(
            String id, String table, String column, String identityColumn) {
        private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

        private DatabaseFieldTarget {
            if (id == null || !id.equals(table + "." + column)
                    || !IDENTIFIER.matcher(table).matches()
                    || !IDENTIFIER.matcher(column).matches()
                    || !IDENTIFIER.matcher(identityColumn).matches()) {
                throw new IllegalArgumentException("invalid database field inventory target");
            }
        }
    }
}
