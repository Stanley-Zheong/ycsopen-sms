package com.ycsopen.sms.core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintCaseEventMigrationTest {

    @Test
    void createsAppendOnlyEventOwnerOnFreshComplaintSchema() {
        EmbeddedDatabase database = complaintDatabase("issue124-fresh-");
        applyEventMigration(database);
        JdbcTemplate jdbc = new JdbcTemplate(database);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM complaint_case_events", Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT event_type FROM complaint_case_events")).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH
                  FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME='complaint_case_events' AND COLUMN_NAME='evidence_text'
                """, Long.class)).isEqualTo(2000L);
    }

    @Test
    void backfillsOnlyProvableLegacyFactsWithFrozenSourceIdentity() {
        EmbeddedDatabase database = complaintDatabase("issue124-existing-");
        JdbcTemplate jdbc = new JdbcTemplate(database);
        LocalDateTime created = LocalDateTime.of(2026, 9, 1, 9, 0);
        LocalDateTime accepted = created.plusHours(1);
        LocalDateTime handled = created.plusHours(2);
        LocalDateTime disposed = created.plusHours(3);
        LocalDateTime recovered = created.plusHours(4);
        LocalDateTime closed = created.plusHours(5);

        jdbc.update("""
                INSERT INTO complaints(source, tenant_id, channel_id, summary, status, attribution_quality,
                                       opinion, remediation, requirement, created_by, created_at,
                                       accepted_by, accepted_at, handled_by, handled_at,
                                       closed_by, closed_at, closed_note, updated_at)
                VALUES ('REGULATOR', 7, 11, 'legacy summary', 'CLOSED', 'PARTIAL',
                        'handled opinion', 'pause channel', 'submit correction', 'creator', ?,
                        'acceptor', ?, 'handler', ?, 'closer', ?, 'closed after review', ?)
                """, Timestamp.valueOf(created), Timestamp.valueOf(accepted), Timestamp.valueOf(handled),
                Timestamp.valueOf(closed), Timestamp.valueOf(closed));
        long complaintId = jdbc.queryForObject("SELECT id FROM complaints", Long.class);
        jdbc.update("""
                INSERT INTO disposal_records(complaint_id, disposal_type, target_ref, disposed_by, disposed_at,
                                             status, authorized_review_id, failure_reason, recovered_by,
                                             recovered_at, resume_condition, original_complaint_id,
                                             idempotency_key)
                VALUES (?, 'SUSPEND_CHANNEL', 'channel:11', 'handler', ?, 'RECOVERED',
                        'recovery-review', 'provider-secret=legacy-canary', 'recoverer', ?, 'manual compensation', ?, 'legacy-key')
                """, complaintId, Timestamp.valueOf(disposed), Timestamp.valueOf(recovered), complaintId);
        long disposalId = jdbc.queryForObject("SELECT id FROM disposal_records", Long.class);
        jdbc.update("""
                INSERT INTO complaints(source, summary, status, attribution_quality, opinion,
                                       created_by, created_at, accepted_by, accepted_at, updated_at)
                VALUES ('CARRIER', 'accepted only', 'PROCESSING', 'UNKNOWN', 'accepted evidence',
                        'creator', ?, 'acceptor', ?, ?)
                """, Timestamp.valueOf(created), Timestamp.valueOf(accepted), Timestamp.valueOf(accepted));
        long acceptedOnlyId = jdbc.queryForObject(
                "SELECT id FROM complaints WHERE summary='accepted only'", Long.class);
        jdbc.update("""
                INSERT INTO complaints(source, summary, status, attribution_quality, created_by, created_at,
                                       handled_by, handled_at, updated_at)
                VALUES ('OPERATOR', 'plain failed remediation', 'PROCESSED', 'UNKNOWN', 'creator', ?,
                        'handler', ?, ?)
                """, Timestamp.valueOf(created), Timestamp.valueOf(handled), Timestamp.valueOf(handled));
        long failedComplaintId = jdbc.queryForObject(
                "SELECT id FROM complaints WHERE summary='plain failed remediation'", Long.class);
        jdbc.update("""
                INSERT INTO disposal_records(complaint_id, disposal_type, target_ref, disposed_by, disposed_at,
                                             status, authorized_review_id, failure_reason, original_complaint_id,
                                             idempotency_key)
                VALUES (?, 'SUSPEND_CHANNEL', 'channel:12', 'handler', ?, 'FAILED',
                        'failed-review', 'provider-secret=plain-canary', ?, 'plain-failed-key')
                """, failedComplaintId, Timestamp.valueOf(disposed), failedComplaintId);

        applyEventMigration(database);

        assertThat(jdbc.queryForList("""
                SELECT event_type FROM complaint_case_events
                 WHERE complaint_id=? ORDER BY occurred_at, id
                """, String.class, complaintId)).containsExactly(
                "REGISTERED", "ACCEPTED", "HANDLED", "REMEDIATION_FAILED", "RECOVERED", "CLOSED");
        assertThat(jdbc.queryForObject("""
                SELECT evidence_text FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='ACCEPTED'
                """, String.class, complaintId)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT review_id FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='REMEDIATION_FAILED'
                """, String.class, complaintId)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT review_id FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='RECOVERED'
                """, String.class, complaintId)).isEqualTo("recovery-review");
        assertThat(jdbc.queryForObject("""
                SELECT evidence_text FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='ACCEPTED'
                """, String.class, acceptedOnlyId)).isEqualTo("accepted evidence");
        assertThat(jdbc.queryForObject("""
                SELECT review_id FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='REMEDIATION_FAILED'
                """, String.class, failedComplaintId)).isEqualTo("failed-review");
        assertThat(jdbc.queryForObject("""
                SELECT failure_reason FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='REMEDIATION_FAILED'
                """, String.class, complaintId)).isEqualTo("处置执行失败，请根据安全审计日志排查")
                .doesNotContain("provider-secret");
        assertThat(jdbc.queryForObject("""
                SELECT failure_reason FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='REMEDIATION_FAILED'
                """, String.class, failedComplaintId)).isEqualTo("处置执行失败，请根据安全审计日志排查")
                .doesNotContain("provider-secret");
        assertThat(jdbc.queryForMap("""
                SELECT source_record_type, source_record_id, related_disposal_id
                  FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='RECOVERED'
                """, complaintId)).containsEntry("source_record_type", "DISPOSAL_RECORD")
                .containsEntry("source_record_id", disposalId)
                .containsEntry("related_disposal_id", disposalId);
    }

    @Test
    void sourceIdentityIsUniqueAndEqualSecondEventsOrderById() {
        EmbeddedDatabase database = complaintDatabase("issue124-identity-");
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("""
                INSERT INTO complaints(source, summary, status, attribution_quality, created_by)
                VALUES ('OPERATOR', 'identity test', 'PENDING', 'UNKNOWN', 'creator')
                """);
        long complaintId = jdbc.queryForObject("SELECT id FROM complaints", Long.class);
        applyEventMigration(database);
        Timestamp sameSecond = Timestamp.valueOf(LocalDateTime.of(2026, 9, 2, 10, 0));

        jdbc.update("""
                INSERT INTO complaint_case_events(
                    complaint_id, event_type, actor, occurred_at, result,
                    source_record_type, source_record_id)
                VALUES (?, 'ACCEPTED', 'operator-a', ?, 'SUCCESS', 'COMPLAINT', ?)
                """, complaintId, sameSecond, complaintId);
        jdbc.update("""
                INSERT INTO complaint_case_events(
                    complaint_id, event_type, actor, occurred_at, result,
                    source_record_type, source_record_id)
                VALUES (?, 'HANDLED', 'operator-b', ?, 'SUCCESS', 'COMPLAINT', ?)
                """, complaintId, sameSecond, complaintId);

        assertThat(jdbc.queryForList("""
                SELECT event_type FROM complaint_case_events
                 WHERE complaint_id=? AND occurred_at=? ORDER BY occurred_at, id
                """, String.class, complaintId, sameSecond)).containsExactly("ACCEPTED", "HANDLED");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO complaint_case_events(
                    complaint_id, event_type, actor, occurred_at, result,
                    source_record_type, source_record_id)
                VALUES (?, 'ACCEPTED', 'duplicate', ?, 'SUCCESS', 'COMPLAINT', ?)
                """, complaintId, sameSecond, complaintId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static EmbeddedDatabase complaintDatabase(String namePrefix) {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName(namePrefix + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
                .addScript("db/migration/V5000__complaint_case_management.sql")
                .build();
    }

    private static void applyEventMigration(EmbeddedDatabase database) {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6600__complaint_case_events.sql"))
                .execute(database);
    }
}
