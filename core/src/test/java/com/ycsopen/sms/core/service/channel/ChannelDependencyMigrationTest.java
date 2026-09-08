package com.ycsopen.sms.core.service.channel;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.ChannelDependencyMigrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelDependencyMigrationTest {
    @Test
    void explicitDestinationMappingsMoveDependenciesBeforeOffline() {
        JdbcTemplate jdbc = ChannelConfigurationTestSupport.jdbc("channel-migration");
        ChannelDependencyInventoryTest.seedChannels(jdbc);
        jdbc.update("INSERT INTO route_rules(id,target_channel_id,status) VALUES (11,1,'ACTIVE')");
        jdbc.update("INSERT INTO channel_group_members(group_id,channel_id) VALUES (20,1)");
        jdbc.update("INSERT INTO signature_channel_registrations(id,channel_id,reg_status) VALUES (31,1,'REGISTERED')");
        jdbc.update("INSERT INTO message_tasks(id,channel_id,send_status) VALUES (41,1,'PENDING')");
        var inventory = new ChannelDependencyInventoryService(jdbc);
        var service = new ChannelRetirementService(jdbc, inventory, new ChannelConfigurationSnapshotRegistry(), null);

        var remaining = service.migrate(7L, 1, new ChannelDependencyMigrationRequest(List.of(
                new ChannelDependencyMigrationRequest.Item("ROUTE_RULE", "11", 2L),
                new ChannelDependencyMigrationRequest.Item("CHANNEL_GROUP_MEMBER", "20", 2L),
                new ChannelDependencyMigrationRequest.Item("SIGNATURE_REGISTRATION", "31", 2L),
                new ChannelDependencyMigrationRequest.Item("MESSAGE_TASK", "41", 2L))));

        assertThat(remaining).isEmpty();
        assertThat(jdbc.queryForObject("SELECT target_channel_id FROM route_rules WHERE id=11", Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT channel_id FROM message_tasks WHERE id=41", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void invalidDependencyReferenceReturnsBusinessError() {
        JdbcTemplate jdbc = ChannelConfigurationTestSupport.jdbc("channel-migration-invalid-ref");
        ChannelDependencyInventoryTest.seedChannels(jdbc);
        var service = new ChannelRetirementService(jdbc, new ChannelDependencyInventoryService(jdbc),
                new ChannelConfigurationSnapshotRegistry(), null);

        assertThatThrownBy(() -> service.migrate(7L, 1, new ChannelDependencyMigrationRequest(List.of(
                new ChannelDependencyMigrationRequest.Item("ROUTE_RULE", "not-a-number", 2L)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CHANNEL_DEPENDENCY_REFERENCE_INVALID"));
    }
}
