package com.ycsopen.sms.core.service.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Versioned platform configuration with server-owned merge and commit-before-reload ordering. */
@Service
public class PlatformConfigurationService {
    private static final int REASON_MAX_LENGTH = 256;

    private final Store store;
    private final PlatformConfigurationRegistry registry;
    private final PlatformConfigurationRuntime runtime;

    @Autowired
    public PlatformConfigurationService(JdbcTemplate jdbc, ObjectMapper json,
                                        PlatformTransactionManager transactions,
                                        PlatformConfigurationRegistry registry,
                                        PlatformConfigurationRuntime runtime) {
        this(new JdbcStore(jdbc, json, new TransactionTemplate(transactions)), registry, runtime);
    }

    PlatformConfigurationService(Store store, PlatformConfigurationRegistry registry,
                                 PlatformConfigurationRuntime runtime) {
        this.store = store;
        this.registry = registry;
        this.runtime = runtime;
    }

    /** Rehydrates a committed active version before the application begins serving console requests. */
    @PostConstruct
    public void initializeRuntime() {
        Current current = current();
        if (current.activeVersion() == 0) {
            return;
        }
        PlatformConfigurationRuntime.Prepared prepared = runtime.prepare(
                current.activeVersion(), current.values());
        runtime.apply(prepared);
        if (!"APPLIED".equals(current.reloadStatus())) {
            store.markApplied(current.activeVersion());
        }
    }

    public ConfigurationView view() {
        Current current = current();
        List<SettingView> settings = registry.definitions().stream().map(definition -> {
            String value = current.values().get(definition.key());
            return new SettingView(definition.key(), definition.label(), definition.type(),
                    registry.displayValue(definition.key(), value),
                    registry.displayValue(definition.key(), definition.defaultValue()),
                    definition.validation(), definition.sensitive(),
                    !definition.sensitive() || value != null);
        }).toList();
        DraftView draft = store.draft().map(version -> new DraftView(
                version.id(), version.baseVersionId(), version.changedKeys(), version.reason(),
                version.createdByName(), version.createdAt())).orElse(null);
        List<HistoryView> history = store.history().stream().map(version -> new HistoryView(
                version.id(), version.sourceVersionId(), version.status(), version.changedKeys(),
                version.reason(), version.createdByName(), version.createdAt(), version.activatedBy(),
                version.activatedAt(), version.reloadStatus(), version.reloadErrorCode())).toList();
        ActiveView active = new ActiveView(current.activeVersion(), current.checksum(),
                current.activatedBy(), current.activatedByName(), current.activatedAt(),
                current.reloadStatus(), current.reloadErrorCode());
        return new ConfigurationView(active, settings, draft, history);
    }

    public long stage(StageCommand command) {
        String reason = requireReason(command.reason());
        if (command.changes() == null || command.changes().isEmpty()) {
            throw new Failure(FailureCode.NO_CHANGES);
        }
        Current current = current();
        if (current.activeVersion() != command.expectedActiveVersion()) {
            throw new Failure(FailureCode.STALE_VERSION);
        }
        Map<String, String> merged;
        try {
            merged = registry.mergeAndValidate(current.values(), command.changes());
        } catch (PlatformConfigurationRegistry.ValidationFailure error) {
            throw new Failure(FailureCode.INVALID_CONFIGURATION, error.key());
        }
        List<String> changedKeys = registry.definitions().stream()
                .map(PlatformConfigurationRegistry.Definition::key)
                .filter(key -> !Objects.equals(current.values().get(key), merged.get(key)))
                .toList();
        if (changedKeys.isEmpty()) {
            throw new Failure(FailureCode.NO_CHANGES);
        }
        return store.insertDraft(command.expectedActiveVersion(), merged, changedKeys,
                registry.checksum(merged), reason, command.actorUserId(), null);
    }

    public Activation activate(ActivateCommand command) {
        String reason = requireReason(command.reason());
        StoredVersion version = store.version(command.versionId());
        PlatformConfigurationRuntime.Prepared prepared;
        try {
            prepared = runtime.prepare(version.id(), version.values());
        } catch (PlatformConfigurationRuntime.ReloadRejected error) {
            store.markReloadRejected(version.id(), error.safeCode());
            throw new Failure(FailureCode.RELOAD_REJECTED, error.safeCode());
        } catch (PlatformConfigurationRegistry.ValidationFailure error) {
            store.markReloadRejected(version.id(), "INVALID_STORED_CONFIGURATION");
            throw new Failure(FailureCode.RELOAD_REJECTED, "INVALID_STORED_CONFIGURATION");
        }

        // TransactionTemplate.execute returns only after the database commit completes.
        store.commitActivation(version.id(), command.expectedActiveVersion(),
                command.actorUserId(), reason);
        runtime.apply(prepared);
        store.markApplied(version.id());
        return new Activation(version.id(), "APPLIED");
    }

    public Activation rollback(RollbackCommand command) {
        String reason = requireReason(command.reason());
        StoredVersion source = store.version(command.sourceVersionId());
        if (!(source.status() == VersionStatus.ACTIVE || source.status() == VersionStatus.SUPERSEDED)) {
            throw new Failure(FailureCode.VERSION_STATE_INVALID);
        }
        Current current = current();
        if (current.activeVersion() != command.expectedActiveVersion()) {
            throw new Failure(FailureCode.STALE_VERSION);
        }
        Map<String, String> sourceValues = registry.normalizeStored(source.values());
        List<String> changedKeys = registry.definitions().stream()
                .map(PlatformConfigurationRegistry.Definition::key)
                .filter(key -> !Objects.equals(current.values().get(key), sourceValues.get(key)))
                .toList();
        if (changedKeys.isEmpty()) {
            throw new Failure(FailureCode.NO_CHANGES);
        }
        long versionId = store.insertDraft(command.expectedActiveVersion(), sourceValues, changedKeys,
                registry.checksum(sourceValues), reason, command.actorUserId(), source.id());
        return activate(new ActivateCommand(versionId, command.expectedActiveVersion(),
                reason, command.actorUserId()));
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty() || normalized.length() > REASON_MAX_LENGTH) {
            throw new Failure(FailureCode.INVALID_REASON);
        }
        return normalized;
    }

    private Current current() {
        Current stored = store.current();
        if (stored.activeVersion() != 0) {
            Map<String, String> normalized = registry.normalizeStored(stored.values());
            return new Current(stored.activeVersion(), stored.lockVersion(), normalized,
                    registry.checksum(normalized), stored.activatedBy(), stored.activatedByName(),
                    stored.activatedAt(), stored.reloadStatus(), stored.reloadErrorCode());
        }
        Map<String, String> defaults = registry.defaults();
        return new Current(0, stored.lockVersion(), defaults, registry.checksum(defaults),
                null, null, null, "APPLIED", null);
    }

    public interface Store {
        Current current();

        long insertDraft(long expectedActiveVersion, Map<String, String> values,
                         List<String> changedKeys, String checksum, String reason,
                         long actorUserId, Long sourceVersionId);

        StoredVersion version(long versionId);

        void commitActivation(long versionId, long expectedActiveVersion,
                              long actorUserId, String reason);

        void markReloadRejected(long versionId, String safeCode);

        void markApplied(long versionId);

        Optional<StoredVersion> draft();

        List<StoredVersion> history();
    }

    private static final class JdbcStore implements Store {
        private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() { };
        private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

        private final JdbcTemplate jdbc;
        private final ObjectMapper json;
        private final TransactionTemplate transactions;

        private JdbcStore(JdbcTemplate jdbc, ObjectMapper json, TransactionTemplate transactions) {
            this.jdbc = jdbc;
            this.json = json;
            this.transactions = transactions;
        }

        @Override
        public Current current() {
            return jdbc.queryForObject("""
                    SELECT s.active_version_id, s.lock_version, s.reload_status AS state_reload_status,
                           s.reload_error_code AS state_reload_error_code,
                           v.values_json, v.checksum, v.activated_by, actor.username AS actor_name,
                           v.activated_at
                    FROM platform_configuration_state s
                    LEFT JOIN platform_configuration_versions v ON v.id = s.active_version_id
                    LEFT JOIN users actor ON actor.id = v.activated_by
                    WHERE s.id = 1
                    """, (result, row) -> {
                Long active = result.getObject("active_version_id", Long.class);
                Map<String, String> values = active == null
                        ? Map.of() : readMap(result.getString("values_json"));
                return new Current(active == null ? 0 : active, result.getLong("lock_version"), values,
                        result.getString("checksum"), result.getObject("activated_by", Long.class),
                        result.getString("actor_name"), timestamp(result, "activated_at"),
                        result.getString("state_reload_status"),
                        result.getString("state_reload_error_code"));
            });
        }

        @Override
        public long insertDraft(long expectedActiveVersion, Map<String, String> values,
                                List<String> changedKeys, String checksum, String reason,
                                long actorUserId, Long sourceVersionId) {
            Long result = transactions.execute(status -> {
                State state = lockedState();
                if (state.activeVersion() != expectedActiveVersion) {
                    throw new Failure(FailureCode.STALE_VERSION);
                }
                jdbc.update("""
                        UPDATE platform_configuration_versions
                        SET status = 'ABANDONED'
                        WHERE status = 'DRAFT'
                        """);
                KeyHolder key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO platform_configuration_versions
                              (base_version_id, source_version_id, status, values_json,
                               changed_keys_json, checksum, reason, created_by, reload_status)
                            VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, ?, 'NOT_APPLIED')
                            """, Statement.RETURN_GENERATED_KEYS);
                    if (expectedActiveVersion == 0) {
                        statement.setNull(1, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(1, expectedActiveVersion);
                    }
                    if (sourceVersionId == null) {
                        statement.setNull(2, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(2, sourceVersionId);
                    }
                    statement.setString(3, write(values));
                    statement.setString(4, write(changedKeys));
                    statement.setString(5, checksum);
                    statement.setString(6, reason);
                    statement.setLong(7, actorUserId);
                    return statement;
                }, key);
                if (key.getKey() == null) {
                    throw new IllegalStateException("configuration version identifier was not generated");
                }
                return key.getKey().longValue();
            });
            if (result == null) {
                throw new IllegalStateException("configuration draft transaction returned no version");
            }
            return result;
        }

        @Override
        public StoredVersion version(long versionId) {
            List<StoredVersion> rows = jdbc.query(versionSql() + " WHERE v.id = ?", this::mapVersion, versionId);
            if (rows.isEmpty()) {
                throw new Failure(FailureCode.VERSION_NOT_FOUND);
            }
            return rows.getFirst();
        }

        @Override
        public void commitActivation(long versionId, long expectedActiveVersion,
                                     long actorUserId, String reason) {
            transactions.executeWithoutResult(status -> {
                State state = lockedState();
                if (state.activeVersion() != expectedActiveVersion) {
                    throw new Failure(FailureCode.STALE_VERSION);
                }
                Map<String, Object> version = jdbc.queryForMap("""
                        SELECT id, base_version_id, status
                        FROM platform_configuration_versions WHERE id = ? FOR UPDATE
                        """, versionId);
                long baseVersion = version.get("base_version_id") == null
                        ? 0 : ((Number) version.get("base_version_id")).longValue();
                if (!"DRAFT".equals(version.get("status")) || baseVersion != expectedActiveVersion) {
                    throw new Failure(FailureCode.VERSION_STATE_INVALID);
                }
                if (expectedActiveVersion != 0) {
                    int superseded = jdbc.update("""
                            UPDATE platform_configuration_versions
                            SET status = 'SUPERSEDED'
                            WHERE id = ? AND status = 'ACTIVE'
                            """, expectedActiveVersion);
                    if (superseded != 1) {
                        throw new Failure(FailureCode.STALE_VERSION);
                    }
                }
                int activated = jdbc.update("""
                        UPDATE platform_configuration_versions
                        SET status = 'ACTIVE', activated_by = ?, activated_at = CURRENT_TIMESTAMP,
                            activation_reason = ?, reload_status = 'PENDING', reload_error_code = NULL
                        WHERE id = ? AND status = 'DRAFT'
                        """, actorUserId, reason, versionId);
                int stateUpdated = jdbc.update("""
                        UPDATE platform_configuration_state
                        SET active_version_id = ?, lock_version = lock_version + 1,
                            reload_status = 'PENDING', reload_error_code = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = 1 AND lock_version = ?
                        """, versionId, state.lockVersion());
                if (activated != 1 || stateUpdated != 1) {
                    throw new Failure(FailureCode.STALE_VERSION);
                }
            });
        }

        @Override
        public void markReloadRejected(long versionId, String safeCode) {
            jdbc.update("""
                    UPDATE platform_configuration_versions
                    SET status = 'RELOAD_REJECTED', reload_status = 'REJECTED', reload_error_code = ?
                    WHERE id = ? AND status = 'DRAFT'
                    """, safeCode, versionId);
        }

        @Override
        public void markApplied(long versionId) {
            transactions.executeWithoutResult(status -> {
                jdbc.update("""
                        UPDATE platform_configuration_versions
                        SET reload_status = 'APPLIED', reload_error_code = NULL
                        WHERE id = ? AND status = 'ACTIVE'
                        """, versionId);
                jdbc.update("""
                        UPDATE platform_configuration_state
                        SET reload_status = 'APPLIED', reload_error_code = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = 1 AND active_version_id = ?
                        """, versionId);
            });
        }

        @Override
        public Optional<StoredVersion> draft() {
            return jdbc.query(versionSql() + " WHERE v.status = 'DRAFT' ORDER BY v.id DESC LIMIT 1",
                    this::mapVersion).stream().findFirst();
        }

        @Override
        public List<StoredVersion> history() {
            return jdbc.query(versionSql() + " ORDER BY v.id DESC LIMIT 50", this::mapVersion);
        }

        private State lockedState() {
            return jdbc.queryForObject("""
                    SELECT active_version_id, lock_version
                    FROM platform_configuration_state WHERE id = 1 FOR UPDATE
                    """, (result, row) -> {
                Long active = result.getObject("active_version_id", Long.class);
                return new State(active == null ? 0 : active, result.getLong("lock_version"));
            });
        }

        private StoredVersion mapVersion(java.sql.ResultSet result, int row) throws java.sql.SQLException {
            Long base = result.getObject("base_version_id", Long.class);
            return new StoredVersion(result.getLong("id"), base == null ? 0 : base,
                    result.getObject("source_version_id", Long.class),
                    VersionStatus.valueOf(result.getString("status")),
                    readMap(result.getString("values_json")), readList(result.getString("changed_keys_json")),
                    result.getString("checksum"), result.getString("reason"),
                    result.getLong("created_by"), result.getString("created_by_name"),
                    timestamp(result, "created_at"), result.getObject("activated_by", Long.class),
                    timestamp(result, "activated_at"), result.getString("reload_status"),
                    result.getString("reload_error_code"));
        }

        private String versionSql() {
            return """
                    SELECT v.id, v.base_version_id, v.source_version_id, v.status, v.values_json,
                           v.changed_keys_json, v.checksum,
                           COALESCE(v.activation_reason, v.reason) AS reason,
                           v.created_by, creator.username AS created_by_name, v.created_at,
                           v.activated_by, v.activated_at, v.reload_status, v.reload_error_code
                    FROM platform_configuration_versions v
                    LEFT JOIN users creator ON creator.id = v.created_by
                    """;
        }

        private String write(Object value) {
            try {
                return json.writeValueAsString(value);
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("configuration JSON serialization failed", error);
            }
        }

        private Map<String, String> readMap(String value) {
            try {
                return Map.copyOf(json.readValue(value, STRING_MAP));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("stored configuration JSON is invalid", error);
            }
        }

        private List<String> readList(String value) {
            try {
                return List.copyOf(json.readValue(value, STRING_LIST));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("stored changed-key JSON is invalid", error);
            }
        }

        private static LocalDateTime timestamp(java.sql.ResultSet result, String column)
                throws java.sql.SQLException {
            java.sql.Timestamp timestamp = result.getTimestamp(column);
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }

    private record State(long activeVersion, long lockVersion) {
    }

    public record StageCommand(long expectedActiveVersion, Map<String, String> changes,
                               String reason, long actorUserId) {
    }

    public record ActivateCommand(long versionId, long expectedActiveVersion,
                                  String reason, long actorUserId) {
    }

    public record RollbackCommand(long sourceVersionId, long expectedActiveVersion,
                                  String reason, long actorUserId) {
    }

    public record Activation(long activeVersion, String reloadStatus) {
    }

    public record ConfigurationView(ActiveView active, List<SettingView> settings,
                                    DraftView draft, List<HistoryView> history) {
    }

    public record ActiveView(long version, String checksum, Long actorUserId, String actor,
                             LocalDateTime activatedAt, String reloadStatus, String reloadErrorCode) {
    }

    public record SettingView(String key, String label, PlatformConfigurationRegistry.ValueType type,
                              String value, String defaultValue, String validation,
                              boolean sensitive, boolean configured) {
    }

    public record DraftView(long version, long baseVersion, List<String> changedKeys,
                            String reason, String actor, LocalDateTime createdAt) {
    }

    public record HistoryView(long version, Long sourceVersion, VersionStatus status,
                              List<String> changedKeys, String reason, String actor,
                              LocalDateTime createdAt, Long activatedBy, LocalDateTime activatedAt,
                              String reloadStatus, String reloadErrorCode) {
    }

    public record Current(long activeVersion, long lockVersion, Map<String, String> values,
                          String checksum, Long activatedBy, String activatedByName,
                          LocalDateTime activatedAt, String reloadStatus, String reloadErrorCode) {
        public Current {
            values = Map.copyOf(values);
        }
    }

    public record StoredVersion(long id, long baseVersionId, Long sourceVersionId,
                                VersionStatus status, Map<String, String> values,
                                List<String> changedKeys, String checksum, String reason,
                                long createdBy, String createdByName, LocalDateTime createdAt,
                                Long activatedBy, LocalDateTime activatedAt,
                                String reloadStatus, String reloadErrorCode) {
        public StoredVersion {
            values = Map.copyOf(values);
            changedKeys = List.copyOf(changedKeys);
        }

        public StoredVersion withStatus(VersionStatus nextStatus, String safeCode) {
            return new StoredVersion(id, baseVersionId, sourceVersionId, nextStatus, values,
                    changedKeys, checksum, reason, createdBy, createdByName, createdAt,
                    activatedBy, activatedAt,
                    nextStatus == VersionStatus.RELOAD_REJECTED ? "REJECTED" : reloadStatus,
                    safeCode);
        }

        public StoredVersion activated(long actorUserId, String activationReason) {
            return new StoredVersion(id, baseVersionId, sourceVersionId, VersionStatus.ACTIVE,
                    values, changedKeys, checksum, activationReason, createdBy, createdByName,
                    createdAt, actorUserId, LocalDateTime.now(), "PENDING", null);
        }

        public StoredVersion withReloadStatus(String nextReloadStatus, String safeCode) {
            return new StoredVersion(id, baseVersionId, sourceVersionId, status, values,
                    changedKeys, checksum, reason, createdBy, createdByName, createdAt,
                    activatedBy, activatedAt, nextReloadStatus, safeCode);
        }
    }

    public enum VersionStatus {
        DRAFT,
        ACTIVE,
        SUPERSEDED,
        ABANDONED,
        RELOAD_REJECTED
    }

    public enum FailureCode {
        INVALID_CONFIGURATION,
        INVALID_REASON,
        NO_CHANGES,
        STALE_VERSION,
        VERSION_NOT_FOUND,
        VERSION_STATE_INVALID,
        RELOAD_REJECTED
    }

    public static final class Failure extends RuntimeException {
        private final FailureCode code;
        private final String detail;

        public Failure(FailureCode code) {
            this(code, null);
        }

        public Failure(FailureCode code, String detail) {
            super(code.name());
            this.code = code;
            this.detail = detail;
        }

        public FailureCode code() {
            return code;
        }

        public String detail() {
            return detail;
        }
    }
}
