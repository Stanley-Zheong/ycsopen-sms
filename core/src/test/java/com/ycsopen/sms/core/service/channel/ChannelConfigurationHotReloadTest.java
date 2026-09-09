package com.ycsopen.sms.core.service.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.ChannelConfigurationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelConfigurationHotReloadTest {
    private JdbcTemplate jdbc;
    private ChannelConfigurationService configurations;
    private ChannelConfigurationSnapshotRegistry registry;
    private ChannelConfigurationVersionService versions;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        jdbc = ChannelConfigurationTestSupport.jdbc("channel-hotload");
        configurations = ChannelConfigurationTestSupport.service(jdbc);
        registry = new ChannelConfigurationSnapshotRegistry();
        json = new ObjectMapper();
        versions = new ChannelConfigurationVersionService(jdbc, configurations, new ChannelConnectivityAdapter(),
                registry, json, null);
    }

    @Test
    void activationCreatesImmutableVersionAndEffectiveSnapshotWithoutRestart() {
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("hotload", "CMPP",
                "channel-fixture.local", 7890, BigDecimal.ONE, 50)).id();

        var result = versions.activate(7L, id, null);

        assertThat(result.resultCode()).isEqualTo("EFFECTIVE");
        assertThat(result.effectiveVersion()).isEqualTo(result.requestedVersion());
        var snapshot = registry.effective(id);
        assertThat(snapshot.versionId()).isEqualTo(result.effectiveVersion());
        assertThat(snapshot.spId()).isEqualTo("SPID10");
        assertThat(snapshot.serviceId()).isEqualTo("svc10");
        assertThat(snapshot.srcId()).isEqualTo("SRC10");
        assertThat(snapshot.tpsLimit()).isEqualTo(100);
        assertThat(snapshot.availability()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_configuration_versions WHERE channel_id=?",
                Integer.class, id)).isEqualTo(1);
        String payload = jdbc.queryForObject("SELECT payload_json FROM channel_configuration_versions WHERE id=?",
                String.class, result.requestedVersion());
        assertThat(payload).contains("\"spId\":\"SPID10\"", "\"serviceId\":\"svc10\"", "\"srcId\":\"SRC10\"",
                "\"tpsLimit\":100", "\"availability\":\"AVAILABLE\"");
    }

    @Test
    void staleExpectedVersionRetainsCurrentEffectiveVersion() {
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("stale", "CMPP",
                "channel-fixture.local", 7890, BigDecimal.ONE, 50)).id();
        Long effective = versions.activate(7L, id, null).effectiveVersion();

        var stale = versions.activate(7L, id, effective + 99);

        assertThat(stale.resultCode()).isEqualTo("STALE_EXPECTED_VERSION");
        assertThat(stale.effectiveVersion()).isEqualTo(effective);
        assertThat(jdbc.queryForObject("SELECT effective_version_id FROM channels WHERE id=?", Long.class, id))
                .isEqualTo(effective);
    }

    @Test
    void activationRejectsDraftChangedAfterPayloadRead() {
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("draft-race", "CMPP",
                "channel-fixture.local", 7890, BigDecimal.ONE, 50)).id();
        var racingConfigurations = new ChannelConfigurationService(jdbc, ChannelConfigurationTestSupport.protector(),
                new ChannelConnectivityAdapter(), json, null) {
            @Override
            ChannelConfigurationVersionService.ChannelPayload payload(long channelId) {
                var payload = super.payload(channelId);
                jdbc.update("UPDATE channels SET configuration_version=configuration_version+1 WHERE id=?", channelId);
                return payload;
            }
        };
        var racingVersions = new ChannelConfigurationVersionService(jdbc, racingConfigurations,
                new ChannelConnectivityAdapter(), registry, json, null);

        var result = racingVersions.activate(7L, id, null);

        assertThat(result.resultCode()).isEqualTo("STALE_EXPECTED_VERSION");
        assertThat(registry.effective(id)).isNull();
        assertThat(jdbc.queryForObject("SELECT effective_version_id FROM channels WHERE id=?", Long.class, id))
                .isNull();
    }

    @Test
    void zeroExpectedVersionIsNormalizedToInitialNullEffectiveVersion() {
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("zero-expected", "CMPP",
                "channel-fixture.local", 7890, BigDecimal.ONE, 50)).id();

        var activated = versions.activate(7L, id, 0L);

        assertThat(activated.resultCode()).isEqualTo("EFFECTIVE");
        assertThat(activated.effectiveVersion()).isEqualTo(activated.requestedVersion());
    }

    @Test
    void retryUsesRequestedFailedVersionPayloadNotCurrentMutableConfiguration() {
        var rejecting = new ChannelConfigurationVersionService(jdbc, configurations, new ChannelConnectivityAdapter() {
            @Override
            public ConnectivityResult validate(ChannelConfiguration configuration) {
                return ConnectivityResult.failed("REJECTED_BY_FIXTURE", true);
            }
        }, registry, json, null);
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("retry-source", "CMPP",
                "channel-fixture.local", 7890, new BigDecimal("0.0100"), 10)).id();
        Long rejectedVersion = rejecting.activate(7L, id, null).requestedVersion();
        configurations.update(7L, id, ChannelConfigurationServiceTest.request("mutable-draft", "CMPP",
                "channel-fixture.local", 7890, new BigDecimal("0.9900"), 90));

        var retry = versions.retry(7L, id, rejectedVersion);

        assertThat(retry.resultCode()).isEqualTo("EFFECTIVE");
        String retryPayload = jdbc.queryForObject("SELECT payload_json FROM channel_configuration_versions WHERE id=?",
                String.class, retry.requestedVersion());
        assertThat(retryPayload).contains("\"name\":\"retry-source\"", "\"price\":0.0100", "\"priority\":10")
                .doesNotContain("mutable-draft", "0.9900");
    }

    @Test
    void rollbackUsesOnlyRequestedEffectiveVersionPayloadNotCurrentMutableConfiguration() {
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("immutable-v1", "CMPP",
                "channel-fixture.local", 7890, new BigDecimal("0.0100"), 10)).id();
        Long v1 = versions.activate(7L, id, null).requestedVersion();
        configurations.update(7L, id, ChannelConfigurationServiceTest.request("mutable-draft", "CMPP",
                "channel-fixture.local", 7890, new BigDecimal("0.9900"), 90));

        var rollback = versions.rollback(7L, id, v1);

        assertThat(rollback.resultCode()).isEqualTo("EFFECTIVE");
        String rollbackPayload = jdbc.queryForObject("SELECT payload_json FROM channel_configuration_versions WHERE id=?",
                String.class, rollback.requestedVersion());
        assertThat(rollbackPayload).contains("\"name\":\"immutable-v1\"", "\"price\":0.0100", "\"priority\":10")
                .doesNotContain("mutable-draft", "0.9900");
    }

    @Test
    void rejectedVersionCannotBeUsedAsRollbackSource() {
        var rejecting = new ChannelConfigurationVersionService(jdbc, configurations, new ChannelConnectivityAdapter() {
            @Override
            public ConnectivityResult validate(ChannelConfiguration configuration) {
                return ConnectivityResult.failed("REJECTED_BY_FIXTURE", true);
            }
        }, registry, json, null);
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("rollback-rejected", "CMPP",
                "channel-fixture.local", 7890, BigDecimal.ONE, 50)).id();
        Long rejectedVersion = rejecting.activate(7L, id, null).requestedVersion();

        assertThatThrownBy(() -> versions.rollback(7L, id, rejectedVersion))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_CONFIGURATION_VERSION_STATUS_INVALID"));
    }

    @Test
    void activationResultNeverCarriesCredentialOrRawExtraJson() {
        long id = configurations.create(7L, new ChannelConfigurationRequest("safe-result", "CMPP", "MOBILE",
                "channel-fixture.local", 7890, "account-a", "Secret-123", "SP", "svc", "src",
                2, 8, 100, BigDecimal.ONE, 50, "00:00-23:59", "AVAILABLE",
                Map.of("secretLike", "Secret-123"), null)).id();

        var result = versions.activate(7L, id, null);

        assertThat(result.toString()).doesNotContain("account-a", "Secret-123", "secretLike");
    }
}
