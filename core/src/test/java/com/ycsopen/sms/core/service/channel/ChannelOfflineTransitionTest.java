package com.ycsopen.sms.core.service.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelOfflineTransitionTest {
    @Test
    void unresolvedDependenciesBlockOfflineAndResolvedChannelIsIdempotentOffline() {
        JdbcTemplate jdbc = ChannelConfigurationTestSupport.jdbc("channel-offline");
        ChannelDependencyInventoryTest.seedChannels(jdbc);
        jdbc.update("INSERT INTO route_rules(target_channel_id,status) VALUES (1,'ACTIVE')");
        var inventory = new ChannelDependencyInventoryService(jdbc);
        var service = new ChannelRetirementService(jdbc, inventory, new ChannelConfigurationSnapshotRegistry(), null);

        var blocked = service.offline(7L, 1);

        assertThat(blocked.changed()).isFalse();
        assertThat(blocked.unresolvedDependencies()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=1", String.class)).isEqualTo("NORMAL");

        jdbc.update("UPDATE route_rules SET target_channel_id=2 WHERE target_channel_id=1");
        var offlined = service.offline(7L, 1);
        var repeated = service.offline(7L, 1);

        assertThat(offlined.changed()).isTrue();
        assertThat(repeated.changed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=1", String.class)).isEqualTo("OFFLINE");
        assertThat(jdbc.queryForObject("SELECT offline_by FROM channels WHERE id=1", String.class)).isEqualTo("7");
    }

    @Test
    void missingChannelReturnsBusinessErrorInsteadOfUnexpectedFailure() {
        JdbcTemplate jdbc = ChannelConfigurationTestSupport.jdbc("channel-offline-missing");
        var service = new ChannelRetirementService(jdbc, new ChannelDependencyInventoryService(jdbc),
                new ChannelConfigurationSnapshotRegistry(), null);

        assertThatThrownBy(() -> service.offline(7L, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_NOT_FOUND"));
    }

    @Test
    void offlineClearsRuntimeSnapshotAndBlocksFurtherConfigurationMutations() {
        JdbcTemplate jdbc = ChannelConfigurationTestSupport.jdbc("channel-offline-immutable");
        var configurations = ChannelConfigurationTestSupport.service(jdbc);
        var registry = new ChannelConfigurationSnapshotRegistry();
        var versions = new ChannelConfigurationVersionService(jdbc, configurations, new ChannelConnectivityAdapter(),
                registry, new ObjectMapper(), null);
        var service = new ChannelRetirementService(jdbc, new ChannelDependencyInventoryService(jdbc), registry, null);
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("offline-immutable", "CMPP",
                "channel-fixture.local", 7890, BigDecimal.ONE, 50)).id();

        var activated = versions.activate(7L, id, null);
        assertThat(registry.effective(id).versionId()).isEqualTo(activated.effectiveVersion());
        var offlined = service.offline(7L, id);

        assertThat(offlined.changed()).isTrue();
        assertThat(registry.effective(id)).isNull();
        assertThatThrownBy(() -> versions.activate(7L, id, activated.effectiveVersion()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_OFFLINE_IMMUTABLE"));
        assertThatThrownBy(() -> configurations.testConnectivity(id))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_OFFLINE_IMMUTABLE"));
        assertThatThrownBy(() -> configurations.update(7L, id, ChannelConfigurationServiceTest.request(
                "offline-immutable-edit", "CMPP", "channel-fixture.local", 7890, BigDecimal.ONE, 50)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_OFFLINE_IMMUTABLE"));
    }
}
