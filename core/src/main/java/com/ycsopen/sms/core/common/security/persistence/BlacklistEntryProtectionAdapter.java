package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Atomic protected writer for blacklist row identity, policy and all queryable blind indexes. */
@Repository
public final class BlacklistEntryProtectionAdapter {

    public static final String SANITIZED_FAILURE = "BLACKLIST_PROTECTION_FAILED";
    private static final String TARGET = "BLACKLIST_ENTRY";
    private static final String FIELD = "mobile";
    private static final String PURPOSE = "MOBILE_BLIND_INDEX";
    private static final Pattern MOBILE = Pattern.compile("1[3-9][0-9]{9}");

    private final ProtectedFieldCodec codec;
    private final BlindIndexPort blindIndexes;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SecureRandom random;

    public BlacklistEntryProtectionAdapter(
            KeyProtectionPort keyProtection,
            BlindIndexPort blindIndexes,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            ActiveFieldKeyReference activeFieldKeyReference) {
        this(new ProtectedFieldCodec(new EnvelopeCodec(), keyProtection, new SecureRandom(),
                        activeFieldKeyReference::current), blindIndexes, jdbc,
                new TransactionTemplate(transactionManager), new SecureRandom());
    }

    BlacklistEntryProtectionAdapter(
            ProtectedFieldCodec codec,
            BlindIndexPort blindIndexes,
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            SecureRandom random) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.blindIndexes = Objects.requireNonNull(blindIndexes, "blindIndexes");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.random = Objects.requireNonNull(random, "random");
    }

    public long create(Long tenantId,
                       String normalizedMobile,
                       BlacklistEntry.ListType listType,
                       BlacklistEntry.Source source,
                       String reason) {
        if (normalizedMobile == null || !MOBILE.matcher(normalizedMobile).matches()
                || listType == null || source == null || tenantId != null && tenantId < 1
                || listType == BlacklistEntry.ListType.WHITE && tenantId == null
                || reason != null && reason.length() > 255) {
            throw rejected();
        }
        long id = positiveRandomLong();
        String scope = tenantId == null ? "global" : "tenant:" + tenantId;
        ProtectionContext context = new ProtectionContext(
                ProtectionContext.Purpose.DATABASE_FIELD, "crypto-storage-bootstrap",
                "blacklist_entries", "mobile_encrypted", scope, "id=" + id);
        byte[] plaintext = normalizedMobile.getBytes(StandardCharsets.US_ASCII);
        byte[] envelope = null;
        byte[] locatorEntropy = new byte[32];
        byte[] originalDigest = null;
        try {
            envelope = codec.protect(plaintext, context, EnvelopeCodec.Target.DATABASE_FIELD);
            BlindIndexPort.Context indexContext = new BlindIndexPort.Context(
                    TARGET, FIELD, BlindIndexPort.Purpose.MOBILE_ROUTING, scope);
            BlindIndexPort.OrderedIndexes indexes = blindIndexes.writeIndexes(
                    normalizedMobile, indexContext);
            random.nextBytes(locatorEntropy);
            String locator = "p3bl_" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(locatorEntropy);
            originalDigest = sha256(locator.getBytes(StandardCharsets.US_ASCII));
            byte[] protectedEnvelope = envelope;
            byte[] boundDigest = originalDigest;
            Long created = transactions.execute(status -> {
                List<ExpectedKey> expectedKeys = lockQueryableKeySet(indexes.values());
                int inserted = jdbc.update("""
                        INSERT INTO blacklist_entries
                            (id,tenant_id,mobile_encrypted,mobile_hash,list_type,reason,source,status)
                        VALUES (?,?,?,?,?,?,?,'ACTIVE')
                        """, id, tenantId, protectedEnvelope, locator, listType.name(), reason,
                        source.name());
                if (inserted != 1) {
                    throw rejected();
                }
                byte[] binding = BlindIndexLookupService.blacklistBinding(id, tenantId, listType,
                        BlacklistEntry.Status.ACTIVE, protectedEnvelope, boundDigest);
                for (VersionedBlindIndex index : indexes.values()) {
                    int indexed = jdbc.update("""
                            INSERT INTO ycs_crypto_blind_indexes
                                (target_type,legacy_row_id,field_id,key_purpose,key_version,
                                 index_value,index_status,original_row_digest,row_binding_digest)
                            SELECT ?,?,?,?,?,?,key_state,?,?
                            FROM ycs_crypto_key_references
                            WHERE purpose=? AND key_version=?
                              AND key_state IN ('ACTIVE','RETIRING')
                            """, TARGET, id, FIELD, PURPOSE, index.keyVersion(),
                            index.canonicalValue(), boundDigest, binding,
                            PURPOSE, index.keyVersion());
                    if (indexed != 1) {
                        throw rejected();
                    }
                }
                BoundRow insertedRow = new BoundRow(id, tenantId, protectedEnvelope, locator,
                        listType, BlacklistEntry.Status.ACTIVE);
                if (requireExactBindings(insertedRow, expectedKeys) != indexes.values().size()) {
                    throw rejected();
                }
                return id;
            });
            if (created == null) {
                throw rejected();
            }
            return created;
        } catch (RuntimeException failure) {
            throw rejected();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(locatorEntropy, (byte) 0);
            clear(envelope);
            clear(originalDigest);
        }
    }

    public void disable(long id) {
        mutate(id, false);
    }

    public void delete(long id) {
        mutate(id, true);
    }

    private void mutate(long id, boolean delete) {
        if (id < 1) {
            throw rejected();
        }
        try {
            transactions.executeWithoutResult(status -> {
                List<ExpectedKey> expectedKeys = lockQueryableKeySet(null);
                BoundRow row = locked(id);
                int boundIndexes = requireExactBindings(row, expectedKeys);
                if (delete) {
                    if (jdbc.update("DELETE FROM ycs_crypto_blind_indexes "
                            + "WHERE target_type=? AND legacy_row_id=? AND field_id=?",
                            TARGET, id, FIELD) < 1
                            || jdbc.update("DELETE FROM blacklist_entries WHERE id=?", id) != 1) {
                        throw rejected();
                    }
                } else {
                    if (row.status() != BlacklistEntry.Status.ACTIVE
                            || jdbc.update("UPDATE blacklist_entries SET status='DISABLED' "
                            + "WHERE id=? AND status='ACTIVE'", id) != 1) {
                        throw rejected();
                    }
                    byte[] updated = BlindIndexLookupService.blacklistBinding(id, row.tenantId(),
                            row.listType(), BlacklistEntry.Status.DISABLED,
                            row.mobileEncrypted(), sha256(
                            row.mobileHash().getBytes(StandardCharsets.UTF_8)));
                    int changed = jdbc.update("UPDATE ycs_crypto_blind_indexes "
                            + "SET row_binding_digest=? WHERE target_type=? AND legacy_row_id=? "
                            + "AND field_id=?", updated, TARGET, id, FIELD);
                    if (changed != boundIndexes) {
                        throw rejected();
                    }
                }
            });
        } catch (RuntimeException failure) {
            throw rejected();
        }
    }

    private BoundRow locked(long id) {
        List<BoundRow> rows = jdbc.query("""
                SELECT entry.id,entry.tenant_id,entry.mobile_encrypted,entry.mobile_hash,
                       entry.list_type,entry.status
                FROM blacklist_entries entry
                WHERE entry.id=?
                FOR UPDATE
                """, (rs, row) -> new BoundRow(rs.getLong("id"),
                rs.getObject("tenant_id", Long.class),
                rs.getBytes("mobile_encrypted"), rs.getString("mobile_hash"),
                BlacklistEntry.ListType.valueOf(rs.getString("list_type")),
                BlacklistEntry.Status.valueOf(rs.getString("status"))), id);
        if (rows.size() != 1) {
            throw rejected();
        }
        return rows.getFirst();
    }

    private List<ExpectedKey> lockQueryableKeySet(List<VersionedBlindIndex> requested) {
        List<ExpectedKey> queryable = jdbc.query("""
                SELECT key_version,key_state
                FROM ycs_crypto_key_references
                WHERE purpose='MOBILE_BLIND_INDEX'
                ORDER BY key_version
                FOR UPDATE
                """, (rs, row) -> new ExpectedKey(rs.getLong("key_version"),
                rs.getString("key_state"))).stream()
                .filter(key -> "ACTIVE".equals(key.state()) || "RETIRING".equals(key.state()))
                .toList();
        if (queryable.isEmpty()) {
            throw rejected();
        }
        if (requested != null && (requested.size() != queryable.size()
                || java.util.stream.IntStream.range(0, requested.size()).anyMatch(index ->
                requested.get(index).keyVersion() != queryable.get(index).version()))) {
            throw rejected();
        }
        return queryable;
    }

    private int requireExactBindings(BoundRow row, List<ExpectedKey> expectedKeys) {
        byte[] original = sha256(row.mobileHash().getBytes(StandardCharsets.UTF_8));
        byte[] expected = BlindIndexLookupService.blacklistBinding(
                row.id(), row.tenantId(), row.listType(), row.status(), row.mobileEncrypted(), original);
        List<IndexBinding> bindings = jdbc.query("""
                SELECT idx.key_version,idx.original_row_digest,idx.row_binding_digest,
                       idx.index_status,kr.key_state
                FROM ycs_crypto_blind_indexes idx
                LEFT JOIN ycs_crypto_key_references kr
                  ON kr.purpose=idx.key_purpose AND kr.key_version=idx.key_version
                WHERE idx.target_type='BLACKLIST_ENTRY' AND idx.legacy_row_id=?
                  AND idx.field_id='mobile'
                ORDER BY idx.key_version
                """, (rs, number) -> new IndexBinding(rs.getLong("key_version"),
                rs.getBytes("original_row_digest"),
                rs.getBytes("row_binding_digest"), rs.getString("index_status"),
                rs.getString("key_state")), row.id());
        if (bindings.size() != expectedKeys.size()
                || java.util.stream.IntStream.range(0, bindings.size()).anyMatch(index -> {
                    IndexBinding binding = bindings.get(index);
                    ExpectedKey key = expectedKeys.get(index);
                    return binding.keyVersion() != key.version()
                            || !Objects.equals(binding.keyState(), key.state())
                            || !MessageDigest.isEqual(original, binding.originalDigest())
                            || !MessageDigest.isEqual(expected, binding.rowBindingDigest())
                            || !Objects.equals(binding.indexStatus(), binding.keyState());
                })) {
            throw rejected();
        }
        return bindings.size();
    }

    private long positiveRandomLong() {
        long value = random.nextLong() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception failure) {
            throw rejected();
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }

    private record BoundRow(long id,
                            Long tenantId,
                            byte[] mobileEncrypted,
                            String mobileHash,
                            BlacklistEntry.ListType listType,
                            BlacklistEntry.Status status) {
    }

    private record ExpectedKey(long version, String state) {
    }

    private record IndexBinding(long keyVersion,
                                byte[] originalDigest,
                                byte[] rowBindingDigest,
                                String indexStatus,
                                String keyState) {
    }
}
