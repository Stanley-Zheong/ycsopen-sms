package com.ycsopen.sms.core.service.channel.health;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.web.dto.ChannelHealthObservationRequest;
import com.ycsopen.sms.core.web.dto.ChannelPauseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelHealthServiceTest {

    @Test
    void recordsHealthMetricsAndRejectsMissingMetric() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("health-metrics");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        var row = service.recordObservation(1L, sample(true, "OK"));

        assertThat(row.healthState()).isEqualTo("HEALTHY");
        assertThat(row.timeoutRate()).isEqualByComparingTo("0.0100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_health_observations", Integer.class)).isOne();
        assertThatThrownBy(() -> service.recordObservation(1L,
                new ChannelHealthObservationRequest(true, null, BigDecimal.ZERO, 1L, "BAD")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_TIMEOUT_RATE_INVALID"));
        assertThatThrownBy(() -> service.recordObservation(1L,
                new ChannelHealthObservationRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, 1L, "BAD")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_CONNECTED_REQUIRED"));
    }

    @Test
    void rateBasedFailureDisplaysDegradedBeforeMaintenanceThreshold() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("health-rate-degraded");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        var row = service.recordObservation(1L, rateFailure("TIMEOUT_RATE_HIGH"));

        assertThat(row.status()).isEqualTo("NORMAL");
        assertThat(row.healthState()).isEqualTo("DEGRADED");
        assertThat(row.reasonCode()).isEqualTo("TIMEOUT_RATE_HIGH");
    }

    @Test
    void sustainedFailureMovesToMaintenanceAndEmitsOnlyOneSourceEvent() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("health-failure");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        service.recordObservation(1L, sample(false, "TIMEOUT"));
        assertThat(channel.getStatus()).isEqualTo(Channel.Status.NORMAL);

        var failed = service.recordObservation(1L, sample(false, "TIMEOUT"));
        var repeated = service.recordObservation(1L, sample(false, "TIMEOUT"));

        assertThat(failed.status()).isEqualTo("MAINTENANCE");
        assertThat(repeated.eventCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_pause_events WHERE event_type='HEALTH_FAILURE'", Integer.class)).isOne();
        assertThat(failed.candidateEligible()).isFalse();
        assertThat(failed.candidateReasonCode()).isEqualTo("STATUS_MAINTENANCE");
    }

    @Test
    void rateBasedFailuresTriggerMaintenanceAndRecoveryAllowsNewEpisode() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("health-rate-failure");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        service.recordObservation(1L, rateFailure("TIMEOUT_RATE_HIGH"));
        var firstEpisode = service.recordObservation(1L, rateFailure("TIMEOUT_RATE_HIGH"));

        assertThat(firstEpisode.status()).isEqualTo("MAINTENANCE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_pause_events WHERE event_type='HEALTH_FAILURE'", Integer.class)).isOne();

        service.recordObservation(1L, sample(true, "OK"));
        service.endMaintenance(1L, new ChannelPauseRequest("MANUAL", "operator", "recovered"));
        assertThat(channel.getStatus()).isEqualTo(Channel.Status.NORMAL);

        service.recordObservation(1L, rateFailure("FAILURE_RATE_HIGH"));
        var secondEpisode = service.recordObservation(1L, rateFailure("FAILURE_RATE_HIGH"));

        assertThat(secondEpisode.status()).isEqualTo("MAINTENANCE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_pause_events WHERE event_type='HEALTH_FAILURE'", Integer.class)).isEqualTo(2);
    }

    @Test
    void pauseRequiresTriggerActorReasonAndRecordsPauseEvidence() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("pause");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        assertThatThrownBy(() -> service.pause(1L, new ChannelPauseRequest("MANUAL", "operator", "")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_PAUSE_REASON_REQUIRED"));

        var paused = service.pause(1L, new ChannelPauseRequest("COMPLAINT", "operator", "投诉集中"));

        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(paused.pauseReason()).isEqualTo("投诉集中");
        assertThat(paused.candidateEligible()).isFalse();
        assertThat(jdbc.queryForObject("SELECT trigger_type FROM channel_pause_events", String.class)).isEqualTo("COMPLAINT");
    }

    @Test
    void maintenanceEndRequiresSuccessfulHealthValidation() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("maintenance");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        service.startMaintenance(1L, new ChannelPauseRequest("MANUAL", "operator", "割接"));
        assertThat(channel.getStatus()).isEqualTo(Channel.Status.MAINTENANCE);
        assertThatThrownBy(() -> service.endMaintenance(1L, new ChannelPauseRequest("MANUAL", "operator", "done")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_MAINTENANCE_HEALTH_REQUIRED"));

        service.recordObservation(1L, sample(true, "OK"));
        var normal = service.endMaintenance(1L, new ChannelPauseRequest("MANUAL", "operator", "done"));

        assertThat(normal.status()).isEqualTo("NORMAL");
        assertThat(normal.candidateEligible()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT reason FROM channel_pause_events
                 WHERE event_type='MAINTENANCE_END' ORDER BY id DESC LIMIT 1
                """, String.class)).isEqualTo("done");
    }

    @Test
    void maintenanceEndIgnoresSuccessfulSampleBeforeMaintenanceStarted() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("maintenance-validation-window");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        service.recordObservation(1L, sample(true, "OK_BEFORE_MAINTENANCE"));
        service.startMaintenance(1L, new ChannelPauseRequest("MANUAL", "operator", "割接"));

        assertThatThrownBy(() -> service.endMaintenance(1L, new ChannelPauseRequest("MANUAL", "operator", "done")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_MAINTENANCE_HEALTH_REQUIRED"));
    }

    @Test
    void resumeClearsPauseStateAndRecordsActorEvidence() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("resume");
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        ChannelHealthService service = service(jdbc, channel);

        service.pause(1L, new ChannelPauseRequest("MANUAL", "operator", "manual hold"));
        var resumed = service.resume(1L, "operator");

        assertThat(resumed.status()).isEqualTo("NORMAL");
        assertThat(resumed.pauseReason()).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT reason FROM channel_pause_events
                 WHERE event_type='RESUME' ORDER BY id DESC LIMIT 1
                """, String.class)).isEqualTo("恢复通道");
    }

    private static ChannelHealthObservationRequest sample(boolean connected, String reason) {
        return new ChannelHealthObservationRequest(connected, new BigDecimal("0.0100"),
                connected ? new BigDecimal("0.0200") : new BigDecimal("0.5000"), 120L, reason);
    }

    private static ChannelHealthObservationRequest rateFailure(String reason) {
        return new ChannelHealthObservationRequest(true, new BigDecimal("0.2000"),
                new BigDecimal("0.2000"), 120L, reason);
    }

    private static ChannelHealthService service(JdbcTemplate jdbc, Channel channel) {
        ChannelRepository channels = mock(ChannelRepository.class);
        when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
        when(channels.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new ChannelHealthService(jdbc, channels, new ChannelCandidateEligibilityService());
    }
}
