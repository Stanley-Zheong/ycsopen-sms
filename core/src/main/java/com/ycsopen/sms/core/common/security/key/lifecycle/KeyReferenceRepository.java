package com.ycsopen.sms.core.common.security.key.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Persistence boundary for non-secret key metadata.
 *
 * <p>There is deliberately no delete operation. Provider keys outlive application metadata and
 * are retired only through explicit state transitions.</p>
 */
public interface KeyReferenceRepository {

    enum Purpose {
        FIELD_ENCRYPTION_KEK,
        MOBILE_BLIND_INDEX,
        OBJECT_CAPABILITY_DIGEST,
        REGISTRATION_UPLOAD_DIGEST,
        SNAPSHOT_RECOVERY;

        boolean usesEncryptionLifecycle() {
            return this == FIELD_ENCRYPTION_KEK || this == SNAPSHOT_RECOVERY;
        }

        boolean isTokenDigest() {
            return this == OBJECT_CAPABILITY_DIGEST || this == REGISTRATION_UPLOAD_DIGEST;
        }
    }

    record KeyReference(Purpose purpose,
                        long keyVersion,
                        String providerId,
                        String providerKeyReference,
                        KeyState state,
                        long wrapOperationCount,
                        boolean rotationRequired,
                        long optimisticVersion) {

        private static final Pattern PROVIDER = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");
        private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

        public KeyReference {
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(state, "state");
            if (keyVersion < 1 || providerId == null || !PROVIDER.matcher(providerId).matches()
                    || providerKeyReference == null
                    || !REFERENCE.matcher(providerKeyReference).matches()
                    || wrapOperationCount < 0 || wrapOperationCount > 1_048_576L
                    || optimisticVersion < 0
                    || rotationRequired != (purpose == Purpose.FIELD_ENCRYPTION_KEK
                    && wrapOperationCount >= 983_040L)) {
                throw new IllegalArgumentException("invalid key reference metadata");
            }
        }

        public boolean sameIdentity(KeyReference other) {
            return other != null && purpose == other.purpose && keyVersion == other.keyVersion
                    && providerId.equals(other.providerId)
                    && providerKeyReference.equals(other.providerKeyReference);
        }
    }

    record Transition(long keyVersion,
                      KeyState expectedState,
                      long expectedOptimisticVersion,
                      KeyState newState) {
        public Transition {
            Objects.requireNonNull(expectedState, "expectedState");
            Objects.requireNonNull(newState, "newState");
            if (keyVersion < 1 || expectedOptimisticVersion < 0 || expectedState == newState) {
                throw new IllegalArgumentException("invalid key transition");
            }
        }
    }

    List<KeyReference> findByPurpose(Purpose purpose);

    List<KeyReference> findAll();

    /** Applies all transitions under one purpose lock; all-or-nothing on any stale input. */
    default boolean transitionAtomically(Purpose purpose, List<Transition> transitions) {
        return transitionAtomicallyGuarded(purpose, transitions, () -> true);
    }

    /** Runs the guard after taking the purpose lock and before any transition. */
    boolean transitionAtomicallyGuarded(
            Purpose purpose,
            List<Transition> transitions,
            BooleanSupplier guard);

    default Optional<KeyReference> uniqueActive(Purpose purpose) {
        Objects.requireNonNull(purpose, "purpose");
        List<KeyReference> active = findByPurpose(purpose).stream()
                .filter(reference -> reference.state().ownsActiveSlot()).toList();
        if (active.size() > 1) {
            throw new IllegalStateException("key lifecycle invariant failed");
        }
        return active.stream().findFirst();
    }

    /** JDBC implementation; SELECT FOR UPDATE serializes activation for one purpose. */
    final class Jdbc implements KeyReferenceRepository {

        private static final String SELECT_COLUMNS = "purpose, key_version, provider_id, "
                + "provider_key_reference, key_state, wrap_operation_count, rotation_required, "
                + "optimistic_version";

        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;

        public Jdbc(JdbcTemplate jdbc, TransactionTemplate transactions) {
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.transactions = Objects.requireNonNull(transactions, "transactions");
        }

        @Override
        public List<KeyReference> findByPurpose(Purpose purpose) {
            Objects.requireNonNull(purpose, "purpose");
            return select("SELECT " + SELECT_COLUMNS + " FROM ycs_crypto_key_references "
                    + "WHERE purpose = ? ORDER BY key_version", purpose.name());
        }

        @Override
        public List<KeyReference> findAll() {
            return select("SELECT " + SELECT_COLUMNS + " FROM ycs_crypto_key_references "
                    + "ORDER BY purpose, key_version");
        }

        @Override
        public boolean transitionAtomically(Purpose purpose, List<Transition> transitions) {
            return transitionAtomicallyGuarded(purpose, transitions, () -> true);
        }

        @Override
        public boolean transitionAtomicallyGuarded(
                Purpose purpose,
                List<Transition> transitions,
                BooleanSupplier guard) {
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(guard, "guard");
            List<Transition> requested = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
            if (requested.isEmpty() || requested.stream().map(Transition::keyVersion).distinct().count()
                    != requested.size()) {
                throw new IllegalArgumentException("invalid key transition set");
            }
            Boolean result = transactions.execute(status -> {
                List<KeyReference> locked = select("SELECT " + SELECT_COLUMNS
                        + " FROM ycs_crypto_key_references WHERE purpose = ? "
                        + "ORDER BY key_version FOR UPDATE", purpose.name());
                for (Transition transition : requested) {
                    KeyReference current = locked.stream()
                            .filter(key -> key.keyVersion() == transition.keyVersion())
                            .findFirst().orElse(null);
                    if (current == null || current.state() != transition.expectedState()
                            || current.optimisticVersion() != transition.expectedOptimisticVersion()) {
                        status.setRollbackOnly();
                        return false;
                    }
                }
                if (!guard.getAsBoolean()) {
                    status.setRollbackOnly();
                    return false;
                }
                long activeBefore = locked.stream().filter(key -> key.state().ownsActiveSlot()).count();
                if (activeBefore > 1) {
                    throw new IllegalStateException("key lifecycle invariant failed");
                }
                Map<Long, Integer> lockedBlindIndexes = purpose == Purpose.MOBILE_BLIND_INDEX
                        ? lockAndValidateBlindIndexes(requested) : Map.of();
                if (lockedBlindIndexes == null) {
                    status.setRollbackOnly();
                    return false;
                }
                for (Transition transition : requested) {
                    int updated = jdbc.update("UPDATE ycs_crypto_key_references SET key_state = ?, "
                                    + "optimistic_version = optimistic_version + 1 "
                                    + "WHERE purpose = ? AND key_version = ? AND key_state = ? "
                                    + "AND optimistic_version = ?",
                            transition.newState().name(), purpose.name(), transition.keyVersion(),
                            transition.expectedState().name(), transition.expectedOptimisticVersion());
                    if (updated != 1) {
                        status.setRollbackOnly();
                        return false;
                    }
                }
                if (purpose == Purpose.MOBILE_BLIND_INDEX
                        && !synchronizeBlindIndexes(requested, lockedBlindIndexes)) {
                    status.setRollbackOnly();
                    return false;
                }
                EnumSet<KeyState> resultingActive = EnumSet.of(KeyState.ACTIVE, KeyState.ROTATION_REQUIRED);
                long activeAfter = locked.stream().map(key -> requested.stream()
                                .filter(change -> change.keyVersion() == key.keyVersion())
                                .findFirst().map(Transition::newState).orElse(key.state()))
                        .filter(resultingActive::contains).count();
                if (activeAfter > 1) {
                    throw new IllegalStateException("key lifecycle invariant failed");
                }
                return true;
            });
            return Boolean.TRUE.equals(result);
        }

        /**
         * Locks every index row whose lifecycle is about to change. Existing metadata must still
         * describe the expected key state; otherwise activation fails instead of publishing a
         * mixed key/index view.
         */
        private Map<Long, Integer> lockAndValidateBlindIndexes(List<Transition> transitions) {
            Map<Long, Integer> counts = new HashMap<>();
            for (Transition transition : transitions) {
                List<String> statuses = jdbc.queryForList(
                        "SELECT index_status FROM ycs_crypto_blind_indexes "
                                + "WHERE key_purpose = 'MOBILE_BLIND_INDEX' AND key_version = ? "
                                + "ORDER BY blind_index_id FOR UPDATE",
                        String.class, transition.keyVersion());
                if (statuses.stream().anyMatch(value -> !transition.expectedState().name().equals(value))) {
                    return null;
                }
                counts.put(transition.keyVersion(), statuses.size());
            }
            return Map.copyOf(counts);
        }

        /** Moves retained ACTIVE metadata to RETIRING in the same transaction as its key. */
        private boolean synchronizeBlindIndexes(
                List<Transition> transitions,
                Map<Long, Integer> lockedCounts) {
            for (Transition transition : transitions) {
                int expected = lockedCounts.getOrDefault(transition.keyVersion(), 0);
                if (transition.newState() != KeyState.ACTIVE
                        && transition.newState() != KeyState.RETIRING) {
                    if (expected != 0) {
                        return false;
                    }
                    continue;
                }
                int updated = jdbc.update(
                        "UPDATE ycs_crypto_blind_indexes SET index_status = ?, "
                                + "optimistic_version = optimistic_version + 1 "
                                + "WHERE key_purpose = 'MOBILE_BLIND_INDEX' AND key_version = ? "
                                + "AND index_status = ?",
                        transition.newState().name(), transition.keyVersion(),
                        transition.expectedState().name());
                if (updated != expected) {
                    return false;
                }
            }
            return true;
        }

        private List<KeyReference> select(String sql, Object... arguments) {
            return jdbc.query(sql, (rs, row) -> new KeyReference(
                    Purpose.valueOf(rs.getString("purpose").toUpperCase(Locale.ROOT)),
                    rs.getLong("key_version"), rs.getString("provider_id"),
                    rs.getString("provider_key_reference"),
                    KeyState.valueOf(rs.getString("key_state").toUpperCase(Locale.ROOT)),
                    rs.getLong("wrap_operation_count"), rs.getBoolean("rotation_required"),
                    rs.getLong("optimistic_version")), arguments);
        }
    }
}
