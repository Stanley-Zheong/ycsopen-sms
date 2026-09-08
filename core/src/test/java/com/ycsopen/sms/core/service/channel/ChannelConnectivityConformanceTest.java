package com.ycsopen.sms.core.service.channel;

import com.ycsopen.sms.core.domain.entity.Channel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelConnectivityConformanceTest {
    private final ChannelConnectivityAdapter adapter = new ChannelConnectivityAdapter();

    @Test
    void acceptsDeterministicFixturesForSupportedProtocols() {
        assertThat(adapter.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.CMPP, "channel-fixture.local", 7890, 2, 8)).passed()).isTrue();
        assertThat(adapter.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.SGIP, "127.0.0.1", 8801, 2, 8)).passed()).isTrue();
        assertThat(adapter.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.SMGP, "localhost", 8890, 2, 8)).passed()).isTrue();
        assertThat(adapter.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.HTTP, "https://channel-fixture.local", 443, 2, 8)).passed()).isTrue();
    }

    @Test
    void rejectsProtocolIncompatibleOrUnreachableValuesWithoutOpeningSockets() {
        assertThat(adapter.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.HTTP, "channel-fixture.local", 443, 2, 8)).reasonCode())
                .isEqualTo("HTTP_ENDPOINT_REQUIRED");
        assertThat(adapter.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.CMPP, "channel-fixture.local", 80, 2, 8)).reasonCode())
                .isEqualTo("PROTOCOL_PORT_INCOMPATIBLE");
        assertThat(adapter.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.CMPP, "198.51.100.10", 7890, 2, 8)).reasonCode())
                .isEqualTo("DETERMINISTIC_FIXTURE_UNREACHABLE");
    }
}
