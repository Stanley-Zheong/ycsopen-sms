package com.ycsopen.sms.core.common.security.key.pkcs11;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/** Durable, monotonic and independently committed admission for AES-GCM KEK wraps. */
public class KekWrapUsageRepository {

    public static final long ROTATION_REQUIRED_AT = 983_040L;
    public static final long HARD_CEILING = 1_048_576L;

    private static final String RESERVE_SQL = """
            UPDATE ycs_crypto_key_references
               SET wrap_operation_count = wrap_operation_count + 1,
                   rotation_required = (wrap_operation_count + 1 >= 983040),
                   key_state = CASE
                       WHEN wrap_operation_count + 1 >= 983040 THEN 'ROTATION_REQUIRED'
                       ELSE key_state
                   END,
                   optimistic_version = optimistic_version + 1
             WHERE purpose = 'FIELD_ENCRYPTION_KEK'
               AND key_version = ?
               AND provider_id = 'pkcs11'
               AND provider_key_reference = ?
               AND key_state IN ('ACTIVE', 'ROTATION_REQUIRED')
               AND wrap_operation_count < 1048576
            """;
    private static final String READ_COUNT_SQL = """
            SELECT wrap_operation_count
              FROM ycs_crypto_key_references
             WHERE purpose = 'FIELD_ENCRYPTION_KEK'
               AND key_version = ?
               AND provider_id = 'pkcs11'
               AND provider_key_reference = ?
            """;

    private final ReservationStore store;
    private final Pkcs11FailureMapper failureMapper;

    public KekWrapUsageRepository(JdbcTemplate jdbcTemplate,
                                  PlatformTransactionManager transactionManager,
                                  Pkcs11FailureMapper failureMapper) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        Objects.requireNonNull(transactionManager, "transactionManager");
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.store = descriptor -> independent.execute(status -> {
            int updated = jdbcTemplate.update(RESERVE_SQL,
                    descriptor.keyVersion(), descriptor.keyReference());
            if (updated != 1) {
                return null;
            }
            return jdbcTemplate.queryForObject(READ_COUNT_SQL, Long.class,
                    descriptor.keyVersion(), descriptor.keyReference());
        });
        this.failureMapper = Objects.requireNonNull(failureMapper, "failureMapper");
    }

    KekWrapUsageRepository(ReservationStore store, Pkcs11FailureMapper failureMapper) {
        this.store = Objects.requireNonNull(store, "store");
        this.failureMapper = Objects.requireNonNull(failureMapper, "failureMapper");
    }

    public Reservation reserve(Pkcs11KeyDescriptor descriptor) {
        if (descriptor == null
                || descriptor.purpose() != Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK
                || !descriptor.state().permitsWrap()) {
            throw failureMapper.failure(Pkcs11FailureMapper.Category.KEY_POLICY, descriptor, null);
        }
        try {
            Long count = store.reserve(descriptor);
            if (count == null || count < 1 || count > HARD_CEILING) {
                throw failureMapper.failure(Pkcs11FailureMapper.Category.WRAP_LIMIT_REACHED,
                        descriptor, null);
            }
            return new Reservation(count, count >= ROTATION_REQUIRED_AT);
        } catch (Pkcs11FailureMapper.Pkcs11OperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failureMapper.failure(Pkcs11FailureMapper.Category.OPERATION_FAILED,
                    descriptor, exception);
        }
    }

    @FunctionalInterface
    interface ReservationStore {
        Long reserve(Pkcs11KeyDescriptor descriptor);
    }

    public record Reservation(long reservedCount, boolean rotationRequired) {
        public Reservation {
            if (reservedCount < 1 || reservedCount > HARD_CEILING
                    || rotationRequired != (reservedCount >= ROTATION_REQUIRED_AT)) {
                throw new IllegalArgumentException("invalid wrap reservation");
            }
        }
    }
}
