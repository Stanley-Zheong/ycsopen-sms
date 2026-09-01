package com.ycsopen.sms.core.common.security.key.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
        List<Reference> references = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (Source source : sources) {
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

    /** Metadata-table sources; database/object-envelope sources must be supplied by their owners. */
    public static List<Source> jdbcMetadataSources(JdbcTemplate jdbc) {
        Objects.requireNonNull(jdbc, "jdbc");
        return List.of(
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
}
