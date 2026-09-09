package com.ycsopen.sms.core.service.channel;

import com.ycsopen.sms.core.web.dto.ChannelDependencyResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChannelDependencyInventoryService {
    private final JdbcTemplate jdbc;

    public ChannelDependencyInventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ChannelDependencyResponse> inventory(long channelId) {
        List<ChannelDependencyResponse> items = new ArrayList<>();
        addRows(items, "ROUTE_RULE", "SELECT id, status FROM route_rules WHERE target_channel_id=?", channelId);
        addRows(items, "CHANNEL_GROUP_MEMBER", "SELECT group_id AS id, 'ACTIVE' AS status FROM channel_group_members WHERE channel_id=?", channelId);
        addRows(items, "SIGNATURE_REGISTRATION", "SELECT id, reg_status AS status FROM signature_channel_registrations WHERE channel_id=?", channelId);
        addRows(items, "MESSAGE_TASK", "SELECT id, send_status AS status FROM message_tasks WHERE channel_id=? AND send_status IN ('PENDING','SENT')", channelId);
        return items;
    }

    @Transactional(readOnly = true)
    public ProviderCounts declaredProviderCounts(long channelId) {
        return new ProviderCounts(count("route_rules", "target_channel_id", channelId),
                count("channel_group_members", "channel_id", channelId),
                count("signature_channel_registrations", "channel_id", channelId),
                count("message_tasks", "channel_id", channelId),
                0);
    }

    private void addRows(List<ChannelDependencyResponse> items, String source, String sql, long channelId) {
        items.addAll(jdbc.query(sql, (row, i) -> new ChannelDependencyResponse(source,
                String.valueOf(row.getLong("id")), row.getString("status"), true, null), channelId));
    }

    private int count(String table, String column, long channelId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?",
                Integer.class, channelId);
        return count == null ? 0 : count;
    }

    public record ProviderCounts(int routeRules, int groupMembers, int signatureRegistrations,
                                 int messageTasks, int priceDependencies) { }
}
