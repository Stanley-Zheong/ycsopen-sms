package com.ycsopen.sms.core.service.channel;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelDependencyInventoryTest {
    @Test
    void inventoryReportsExistingReferencesAndExplicitZeroForUnimplementedPriceProvider() {
        JdbcTemplate jdbc = ChannelConfigurationTestSupport.jdbc("channel-inventory");
        seedChannels(jdbc);
        jdbc.update("INSERT INTO route_rules(target_channel_id,status) VALUES (1,'ACTIVE')");
        jdbc.update("INSERT INTO channel_group_members(group_id,channel_id) VALUES (20,1)");
        jdbc.update("INSERT INTO signature_channel_registrations(channel_id,reg_status) VALUES (1,'REGISTERED')");
        jdbc.update("INSERT INTO message_tasks(channel_id,send_status) VALUES (1,'PENDING')");

        var service = new ChannelDependencyInventoryService(jdbc);

        assertThat(service.inventory(1)).extracting("source")
                .containsExactlyInAnyOrder("ROUTE_RULE", "CHANNEL_GROUP_MEMBER", "SIGNATURE_REGISTRATION", "MESSAGE_TASK");
        assertThat(service.declaredProviderCounts(1).priceDependencies()).isZero();
    }

    static void seedChannels(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO channels(id, channel_name, protocol, operator, host, port, price)
                VALUES (1,'source','CMPP','MOBILE','channel-fixture.local',7890,0.0100),
                       (2,'destination','CMPP','MOBILE','channel-fixture.local',7891,0.0100)
                """);
    }
}
