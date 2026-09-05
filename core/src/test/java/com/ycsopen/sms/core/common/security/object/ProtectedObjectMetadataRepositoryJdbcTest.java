package com.ycsopen.sms.core.common.security.object;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Focused SQL-state regression tests for protected-object deletion finalization. */
class ProtectedObjectMetadataRepositoryJdbcTest {

    @Test
    void deletionDoesNotCompleteFailedOperationOrReleaseItsFieldReservation() {
        Fixture fixture = fixture();
        String objectId = objectId('F');
        fixture.insertObject(objectId);
        fixture.insertOperation(objectId, "FAILED");

        fixture.repository().markDeleted(objectId);

        assertThat(fixture.objectState(objectId)).isEqualTo("DELETED");
        assertThat(fixture.operationState(objectId)).isEqualTo("FAILED");
        assertThat(fixture.fieldPurpose(objectId)).isEqualTo("FIELD_ENCRYPTION_KEK");
        assertThat(fixture.fieldVersion(objectId)).isEqualTo(7L);
    }

    @Test
    void deletionReleasesFieldReservationOnlyFromEligibleOperationStates() {
        Fixture fixture = fixture();
        List<String> eligibleStates = List.of(
                "OBJECT_STORED", "RECONCILE_DELETE", "COMPLETED");

        for (int index = 0; index < eligibleStates.size(); index++) {
            String objectId = objectId((char) ('A' + index));
            fixture.insertObject(objectId);
            fixture.insertOperation(objectId, eligibleStates.get(index));

            fixture.repository().markDeleted(objectId);

            assertThat(fixture.operationState(objectId)).isEqualTo("COMPLETED");
            assertThat(fixture.fieldPurpose(objectId)).isNull();
            assertThat(fixture.fieldVersion(objectId)).isNull();
        }
    }

    private static Fixture fixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE ycs_crypto_protected_objects (
                    protected_object_id VARCHAR(80) PRIMARY KEY,
                    object_state VARCHAR(24) NOT NULL,
                    optimistic_version BIGINT NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE ycs_crypto_object_operations (
                    operation_id VARCHAR(36) PRIMARY KEY,
                    protected_object_id VARCHAR(80),
                    operation_state VARCHAR(24) NOT NULL,
                    affected_count BIGINT NOT NULL DEFAULT 0,
                    field_key_purpose VARCHAR(48),
                    field_key_version BIGINT,
                    optimistic_version BIGINT NOT NULL DEFAULT 0
                )
                """);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        return new Fixture(jdbc,
                new ProtectedObjectMetadataRepository(jdbc, transactions));
    }

    private static String objectId(char fill) {
        return "pobj_v1_" + String.valueOf(fill).repeat(32);
    }

    private record Fixture(JdbcTemplate jdbc,
                           ProtectedObjectMetadataRepository repository) {

        void insertObject(String objectId) {
            jdbc.update("""
                    INSERT INTO ycs_crypto_protected_objects
                        (protected_object_id, object_state)
                    VALUES (?, 'DELETING')
                    """, objectId);
        }

        void insertOperation(String objectId, String state) {
            jdbc.update("""
                    INSERT INTO ycs_crypto_object_operations
                        (operation_id, protected_object_id, operation_state,
                         field_key_purpose, field_key_version)
                    VALUES (?, ?, ?, 'FIELD_ENCRYPTION_KEK', 7)
                    """, UUID.randomUUID().toString(), objectId, state);
        }

        String objectState(String objectId) {
            return jdbc.queryForObject("""
                    SELECT object_state FROM ycs_crypto_protected_objects
                    WHERE protected_object_id = ?
                    """, String.class, objectId);
        }

        String operationState(String objectId) {
            return jdbc.queryForObject("""
                    SELECT operation_state FROM ycs_crypto_object_operations
                    WHERE protected_object_id = ?
                    """, String.class, objectId);
        }

        String fieldPurpose(String objectId) {
            return jdbc.queryForObject("""
                    SELECT field_key_purpose FROM ycs_crypto_object_operations
                    WHERE protected_object_id = ?
                    """, String.class, objectId);
        }

        Long fieldVersion(String objectId) {
            return jdbc.queryForObject("""
                    SELECT field_key_version FROM ycs_crypto_object_operations
                    WHERE protected_object_id = ?
                    """, Long.class, objectId);
        }
    }
}
