package com.ycsopen.sms.core.service.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelConfigurationActivationFaultTest {
    @Test
    void rejectedAdapterResultKeepsPriorEffectiveVersionRetryable() {
        JdbcTemplate jdbc = ChannelConfigurationTestSupport.jdbc("channel-fault");
        var configurations = ChannelConfigurationTestSupport.service(jdbc);
        var registry = new ChannelConfigurationSnapshotRegistry();
        var rejecting = new ChannelConnectivityAdapter() {
            @Override
            public ConnectivityResult validate(ChannelConfiguration configuration) {
                return ConnectivityResult.failed("REJECTED_BY_FIXTURE", true);
            }
        };
        var versions = new ChannelConfigurationVersionService(jdbc, configurations, rejecting,
                registry, new ObjectMapper(), null);
        long id = configurations.create(7L, ChannelConfigurationServiceTest.request("fault", "CMPP",
                "channel-fixture.local", 7890, BigDecimal.ONE, 50)).id();

        var result = versions.activate(7L, id, null);

        assertThat(result.resultCode()).isEqualTo("REJECTED");
        assertThat(result.retryable()).isTrue();
        assertThat(result.effectiveVersion()).isNull();
        assertThat(jdbc.queryForObject("SELECT effective_version_id FROM channels WHERE id=?", Long.class, id))
                .isNull();
    }
}
