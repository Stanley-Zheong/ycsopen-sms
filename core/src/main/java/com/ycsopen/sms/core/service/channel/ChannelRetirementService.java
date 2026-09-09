package com.ycsopen.sms.core.service.channel;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.web.dto.ChannelDependencyMigrationRequest;
import com.ycsopen.sms.core.web.dto.ChannelDependencyResponse;
import com.ycsopen.sms.core.web.dto.ChannelOfflineResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChannelRetirementService {
    private final JdbcTemplate jdbc;
    private final ChannelDependencyInventoryService inventory;
    private final ChannelConfigurationSnapshotRegistry registry;
    private final OperationAuditService audits;

    public ChannelRetirementService(JdbcTemplate jdbc, ChannelDependencyInventoryService inventory,
                                    ChannelConfigurationSnapshotRegistry registry, OperationAuditService audits) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.registry = registry;
        this.audits = audits;
    }

    @Transactional
    public List<ChannelDependencyResponse> migrate(long actorUserId, long channelId,
                                                   ChannelDependencyMigrationRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("CHANNEL_DEPENDENCY_MAPPING_REQUIRED", "必须提供依赖迁移目标");
        }
        for (ChannelDependencyMigrationRequest.Item item : request.items()) {
            long destination = validDestination(channelId, item.destinationChannelId());
            long referenceId = parseReferenceId(item.referenceId());
            switch (item.source()) {
                case "ROUTE_RULE" -> jdbc.update("UPDATE route_rules SET target_channel_id=? WHERE id=? AND target_channel_id=?",
                        destination, referenceId, channelId);
                case "CHANNEL_GROUP_MEMBER" -> {
                    int exists = jdbc.queryForObject("SELECT COUNT(*) FROM channel_group_members WHERE group_id=? AND channel_id=?",
                            Integer.class, referenceId, destination);
                    if (exists == 0) {
                        jdbc.update("UPDATE channel_group_members SET channel_id=? WHERE group_id=? AND channel_id=?",
                                destination, referenceId, channelId);
                    } else {
                        jdbc.update("DELETE FROM channel_group_members WHERE group_id=? AND channel_id=?",
                                referenceId, channelId);
                    }
                }
                case "SIGNATURE_REGISTRATION" -> jdbc.update("UPDATE signature_channel_registrations SET channel_id=? WHERE id=? AND channel_id=?",
                        destination, referenceId, channelId);
                case "MESSAGE_TASK" -> jdbc.update("UPDATE message_tasks SET channel_id=? WHERE id=? AND channel_id=?",
                        destination, referenceId, channelId);
                default -> throw new BusinessException("CHANNEL_DEPENDENCY_SOURCE_INVALID", "依赖来源不合法");
            }
        }
        audit(actorUserId, channelId, "CHANNEL_DEPENDENCY_MIGRATE", "SUCCESS");
        return inventory.inventory(channelId);
    }

    @Transactional
    public ChannelOfflineResponse offline(long actorUserId, long channelId) {
        String status = status(channelId);
        if ("OFFLINE".equals(status)) {
            return new ChannelOfflineResponse(channelId, "OFFLINE", false, List.of());
        }
        List<ChannelDependencyResponse> unresolved = inventory.inventory(channelId);
        if (!unresolved.isEmpty()) {
            audit(actorUserId, channelId, "CHANNEL_OFFLINE_BLOCKED", "CLIENT_FAILURE");
            return new ChannelOfflineResponse(channelId, status, false, unresolved);
        }
        jdbc.update("UPDATE channels SET status='OFFLINE', offline_by=?, offline_at=CURRENT_TIMESTAMP WHERE id=? AND status<>'OFFLINE'",
                String.valueOf(actorUserId), channelId);
        registry.restore(channelId, null);
        audit(actorUserId, channelId, "CHANNEL_OFFLINE", "SUCCESS");
        return new ChannelOfflineResponse(channelId, "OFFLINE", true, List.of());
    }

    private long validDestination(long sourceChannelId, Long destinationChannelId) {
        if (destinationChannelId == null || destinationChannelId == sourceChannelId) {
            throw new BusinessException("CHANNEL_DEPENDENCY_DESTINATION_INVALID", "迁移目标通道不合法");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM channels WHERE id=? AND status<>'OFFLINE'",
                Integer.class, destinationChannelId);
        if (exists == null || exists == 0) {
            throw new BusinessException("CHANNEL_DEPENDENCY_DESTINATION_INVALID", "迁移目标通道不存在或已下线");
        }
        return destinationChannelId;
    }

    private String status(long channelId) {
        try {
            return jdbc.queryForObject("SELECT status FROM channels WHERE id=?", String.class, channelId);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("CHANNEL_NOT_FOUND", "通道不存在");
        }
    }

    private long parseReferenceId(String referenceId) {
        try {
            return Long.parseLong(referenceId);
        } catch (RuntimeException ex) {
            throw new BusinessException("CHANNEL_DEPENDENCY_REFERENCE_INVALID", "依赖引用不合法");
        }
    }

    private void audit(long actorUserId, long channelId, String operation, String result) {
        if (audits != null) {
            audits.append(new OperationAuditService.AuditCommand(actorUserId, operation, "CHANNEL",
                    String.valueOf(channelId), "INTERNAL", "/api/v1/console/channels/configuration/offline",
                    "{\"channelId\":" + channelId + "}", result, 200, "internal", null, 0));
        }
    }
}
