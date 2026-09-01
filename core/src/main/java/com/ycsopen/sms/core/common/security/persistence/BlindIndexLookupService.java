package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.repository.BlacklistEntryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Metadata-first, checkpoint-aware sole owner of protected mobile equality lookup. */
@Service
public class BlindIndexLookupService {

    public static final String SANITIZED_FAILURE = "BLIND_INDEX_LOOKUP_FAILED";

    private static final String BLACKLIST_TARGET = "BLACKLIST_ENTRY";
    private static final String FIELD_ID = "mobile";
    private static final String KEY_PURPOSE = "MOBILE_BLIND_INDEX";
    private static final Set<String> TARGET_STATES = Set.of(
            "DISCOVERED", "BACKFILLED", "VERIFIED", "CUTOVER", "SCRUBBED", "COMPLETE");

    private final JdbcTemplate jdbcTemplate;
    private final LegacyMobileHashReader legacyReader;

    public BlindIndexLookupService(JdbcTemplate jdbcTemplate,
                                   BlacklistEntryRepository blacklistEntryRepository) {
        this(jdbcTemplate, new LegacyMobileHashReader(blacklistEntryRepository));
    }

    BlindIndexLookupService(JdbcTemplate jdbcTemplate, LegacyMobileHashReader legacyReader) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.legacyReader = Objects.requireNonNull(legacyReader, "legacyReader");
    }

    public BlacklistLookupResult lookupBlacklist(long tenantId,
                                                 LegacyMobileLookupToken token,
                                                 BlacklistEntry.Status status) {
        if (tenantId <= 0 || token == null || status == null) {
            throw failure();
        }
        try {
            TargetPolicy policy = targetPolicy(BLACKLIST_TARGET);
            BlindIndexPort.OrderedIndexes indexes = token.blacklistIndexes();
            assertExactQueryableKeySet(indexes);

            List<BlacklistMatch> candidates = new ArrayList<>(metadataMatches(indexes));
            if (!policy.complete() && policy.legacyFallbackAllowed()) {
                candidates.addAll(legacyReader.readBlacklist(token, tenantId, status));
            }
            Map<Long, BlacklistMatch> deduplicated = deduplicate(candidates);

            boolean tenantWhitelist = deduplicated.values().stream()
                    .anyMatch(match -> Objects.equals(match.tenantId(), tenantId)
                            && match.status() == status
                            && match.listType() == BlacklistEntry.ListType.WHITE);
            if (tenantWhitelist) {
                return BlacklistLookupResult.whitelisted();
            }
            boolean systemBlacklist = deduplicated.values().stream()
                    .anyMatch(match -> match.tenantId() == null
                            && match.status() == status
                            && match.listType() == BlacklistEntry.ListType.BLACK);
            if (systemBlacklist) {
                return BlacklistLookupResult.blockedBySystem();
            }
            boolean tenantBlacklist = deduplicated.values().stream()
                    .anyMatch(match -> Objects.equals(match.tenantId(), tenantId)
                            && match.status() == status
                            && match.listType() == BlacklistEntry.ListType.BLACK);
            return tenantBlacklist
                    ? BlacklistLookupResult.blockedByTenant()
                    : BlacklistLookupResult.unmatched();
        } catch (IllegalStateException failure) {
            if (SANITIZED_FAILURE.equals(failure.getMessage())) {
                throw failure;
            }
            throw failure();
        } catch (RuntimeException failure) {
            throw failure();
        }
    }

    BlindIndexPort.OrderedIndexes portabilityIndexes(LegacyMobileLookupToken token) {
        if (token == null) {
            throw failure();
        }
        return token.portabilityIndexes();
    }

    private TargetPolicy targetPolicy(String target) {
        List<TargetPolicy> policies = jdbcTemplate.query("""
                SELECT target_state, legacy_fallback_allowed
                FROM ycs_crypto_migration_targets
                WHERE target_type = ?
                """, (rs, row) -> new TargetPolicy(rs.getString(1), rs.getBoolean(2)), target);
        if (policies.size() != 1 || !TARGET_STATES.contains(policies.getFirst().state())
                || (policies.getFirst().complete() && policies.getFirst().legacyFallbackAllowed())) {
            throw failure();
        }
        return policies.getFirst();
    }

    private void assertExactQueryableKeySet(BlindIndexPort.OrderedIndexes indexes) {
        List<KeyState> keys = jdbcTemplate.query("""
                SELECT key_version, key_state
                FROM ycs_crypto_key_references
                WHERE purpose = ? AND key_state IN ('ACTIVE', 'RETIRING')
                ORDER BY key_version
                """, (rs, row) -> new KeyState(rs.getInt(1), rs.getString(2)), KEY_PURPOSE);
        if (keys.isEmpty() || keys.size() != indexes.values().size()) {
            throw failure();
        }
        for (int index = 0; index < keys.size(); index++) {
            KeyState key = keys.get(index);
            VersionedBlindIndex supplied = indexes.values().get(index);
            if (key.version() != supplied.keyVersion()
                    || !("ACTIVE".equals(key.state()) || "RETIRING".equals(key.state()))) {
                throw failure();
            }
        }
    }

    private List<BlacklistMatch> metadataMatches(BlindIndexPort.OrderedIndexes indexes) {
        StringBuilder requested = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        for (int index = 0; index < indexes.values().size(); index++) {
            if (index > 0) {
                requested.append(" UNION ALL ");
            }
            requested.append("SELECT CAST(? AS DECIMAL(20, 0)) AS key_version, "
                    + "CAST(? AS CHAR(53)) AS index_value");
            VersionedBlindIndex value = indexes.values().get(index);
            parameters.add(value.keyVersion());
            parameters.add(value.canonicalValue());
        }
        parameters.add(BLACKLIST_TARGET);
        parameters.add(FIELD_ID);
        return jdbcTemplate.query("""
                SELECT bi.legacy_row_id, bi.index_status, bi.original_row_digest,
                       kr.key_state, entry.id, entry.tenant_id, entry.list_type, entry.status
                FROM ycs_crypto_blind_indexes bi
                JOIN (%s) requested
                  ON requested.key_version = bi.key_version
                 AND requested.index_value = bi.index_value
                LEFT JOIN ycs_crypto_key_references kr
                  ON kr.purpose = bi.key_purpose AND kr.key_version = bi.key_version
                LEFT JOIN blacklist_entries entry ON entry.id = bi.legacy_row_id
                WHERE bi.target_type = ? AND bi.field_id = ?
                ORDER BY bi.legacy_row_id, bi.key_version
                """.formatted(requested), (rs, row) -> metadataMatch(rs), parameters.toArray());
    }

    private static BlacklistMatch metadataMatch(ResultSet rs) throws SQLException {
        long legacyRowId = rs.getLong("legacy_row_id");
        byte[] digest = rs.getBytes("original_row_digest");
        String indexStatus = rs.getString("index_status");
        String keyState = rs.getString("key_state");
        Long resolvedId = rs.getObject("id", Long.class);
        String listType = rs.getString("list_type");
        String status = rs.getString("status");
        if (legacyRowId <= 0 || resolvedId == null || resolvedId != legacyRowId
                || digest == null || digest.length != 32
                || !("ACTIVE".equals(keyState) || "RETIRING".equals(keyState))
                || !Objects.equals(indexStatus, keyState)
                || listType == null || status == null) {
            throw failure();
        }
        return new BlacklistMatch(resolvedId, rs.getObject("tenant_id", Long.class),
                BlacklistEntry.ListType.valueOf(listType), BlacklistEntry.Status.valueOf(status), digest);
    }

    private static Map<Long, BlacklistMatch> deduplicate(List<BlacklistMatch> matches) {
        Map<Long, BlacklistMatch> rows = new LinkedHashMap<>();
        for (BlacklistMatch match : matches) {
            BlacklistMatch existing = rows.putIfAbsent(match.id(), match);
            if (existing != null && !existing.sameBinding(match)) {
                throw failure();
            }
        }
        return rows;
    }

    private static IllegalStateException failure() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }

    record BlacklistMatch(Long id,
                          Long tenantId,
                          BlacklistEntry.ListType listType,
                          BlacklistEntry.Status status,
                          byte[] originalRowDigest) {
        BlacklistMatch {
            originalRowDigest = originalRowDigest == null ? null : originalRowDigest.clone();
        }

        boolean sameBinding(BlacklistMatch other) {
            return Objects.equals(id, other.id)
                    && Objects.equals(tenantId, other.tenantId)
                    && listType == other.listType
                    && status == other.status
                    && (originalRowDigest == null || other.originalRowDigest == null
                        || Arrays.equals(originalRowDigest, other.originalRowDigest));
        }

        @Override
        public byte[] originalRowDigest() {
            return originalRowDigest == null ? null : originalRowDigest.clone();
        }
    }

    private record TargetPolicy(String state, boolean legacyFallbackAllowed) {
        boolean complete() {
            return "COMPLETE".equals(state);
        }
    }

    private record KeyState(int version, String state) {
    }

    public record BlacklistLookupResult(boolean tenantWhitelist,
                                        boolean blocked,
                                        BlockReason blockReason) {
        public enum BlockReason { NONE, SYSTEM_BLACKLIST, TENANT_BLACKLIST }

        static BlacklistLookupResult whitelisted() {
            return new BlacklistLookupResult(true, false, BlockReason.NONE);
        }

        static BlacklistLookupResult blockedBySystem() {
            return new BlacklistLookupResult(false, true, BlockReason.SYSTEM_BLACKLIST);
        }

        static BlacklistLookupResult blockedByTenant() {
            return new BlacklistLookupResult(false, true, BlockReason.TENANT_BLACKLIST);
        }

        static BlacklistLookupResult unmatched() {
            return new BlacklistLookupResult(false, false, BlockReason.NONE);
        }
    }
}
