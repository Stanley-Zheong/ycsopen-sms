package com.ycsopen.sms.core.service.channel.health;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.web.dto.ChannelPoolRequest;
import com.ycsopen.sms.core.web.dto.ChannelPoolResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ChannelPoolService {
    private final JdbcTemplate jdbc;
    private final ChannelRepository channels;
    private final ChannelCandidateEligibilityService eligibility;

    public ChannelPoolService(JdbcTemplate jdbc, ChannelRepository channels, ChannelCandidateEligibilityService eligibility) {
        this.jdbc = jdbc;
        this.channels = channels;
        this.eligibility = eligibility;
    }

    @Transactional(readOnly = true)
    public List<ChannelPoolResponse> list() {
        return jdbc.query("""
                SELECT id, pool_name, mode, version, status FROM channel_pools ORDER BY id DESC
                """, (row, i) -> pool(row.getLong("id"), row.getString("pool_name"), row.getString("mode"),
                row.getLong("version"), row.getString("status")));
    }

    @Transactional
    public ChannelPoolResponse create(ChannelPoolRequest request) {
        Validated validated = validate(request, null);
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO channel_pools(pool_name, mode, version, status) VALUES (?, ?, 0, 'ACTIVE')
                    """, new String[]{"id"});
            statement.setString(1, validated.name());
            statement.setString(2, validated.mode());
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        replaceMembers(id, validated.members());
        return get(id);
    }

    @Transactional
    public ChannelPoolResponse update(long id, ChannelPoolRequest request) {
        if (request == null || request.expectedVersion() == null) {
            throw new BusinessException("CHANNEL_POOL_STALE", "通道池版本已变化");
        }
        Validated validated = validate(request, id);
        int changed = jdbc.update("""
                UPDATE channel_pools SET pool_name=?, mode=?, version=version+1
                 WHERE id=? AND version=?
                """, validated.name(), validated.mode(), id, request.expectedVersion());
        if (changed == 0) {
            boolean exists = Boolean.TRUE.equals(jdbc.query("""
                    SELECT 1 FROM channel_pools WHERE id=?
                    """, (row, i) -> true, id).stream().findFirst().orElse(false));
            if (!exists) {
                throw new BusinessException("CHANNEL_POOL_NOT_FOUND", "通道池不存在");
            }
            throw new BusinessException("CHANNEL_POOL_STALE", "通道池版本已变化");
        }
        replaceMembers(id, validated.members());
        return get(id);
    }

    @Transactional(readOnly = true)
    public ChannelPoolResponse get(long id) {
        return jdbc.query("""
                SELECT id, pool_name, mode, version, status FROM channel_pools WHERE id=?
                """, (row, i) -> pool(row.getLong("id"), row.getString("pool_name"), row.getString("mode"),
                row.getLong("version"), row.getString("status")), id).stream().findFirst()
                .orElseThrow(() -> new BusinessException("CHANNEL_POOL_NOT_FOUND", "通道池不存在"));
    }

    private ChannelPoolResponse pool(long id, String name, String mode, long version, String status) {
        List<ChannelPoolResponse.Member> members = jdbc.query("""
                SELECT channel_id, weight, primary_member, enabled FROM channel_pool_members
                 WHERE pool_id=? ORDER BY id
                """, (row, i) -> new ChannelPoolResponse.Member(row.getLong("channel_id"),
                row.getInt("weight"), row.getBoolean("primary_member"), row.getBoolean("enabled")), id);
        return new ChannelPoolResponse(id, name, mode, version, status, members);
    }

    private Validated validate(ChannelPoolRequest request, Long currentId) {
        if (request == null) {
            throw new BusinessException("CHANNEL_POOL_REQUIRED", "通道池不能为空");
        }
        String name = required(request.name(), "CHANNEL_POOL_NAME_REQUIRED", "通道池名称不能为空");
        String mode = required(request.mode(), "CHANNEL_POOL_MODE_REQUIRED", "通道池模式不能为空").toUpperCase();
        if (!List.of("WEIGHTED", "PRIMARY_BACKUP").contains(mode)) {
            throw new BusinessException("CHANNEL_POOL_MODE_INVALID", "通道池模式不合法");
        }
        if (request.members() == null || request.members().isEmpty()) {
            throw new BusinessException("CHANNEL_POOL_MEMBER_REQUIRED", "通道池成员不能为空");
        }
        int duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM channel_pools WHERE pool_name=? AND (? IS NULL OR id<>?)
                """, Integer.class, name, currentId, currentId);
        if (duplicate > 0) {
            throw new BusinessException("CHANNEL_POOL_NAME_DUPLICATED", "通道池名称已存在");
        }
        List<ChannelPoolRequest.Member> members = request.members();
        Set<Long> seenChannels = new HashSet<>();
        for (ChannelPoolRequest.Member member : members) {
            if (member == null || !seenChannels.add(member.channelId())) {
                throw new BusinessException("CHANNEL_POOL_MEMBER_DUPLICATED", "通道池成员不能重复");
            }
        }
        long enabledCount = members.stream().filter(ChannelPoolRequest.Member::enabled).count();
        if (enabledCount == 0) {
            throw new BusinessException("CHANNEL_POOL_ENABLED_MEMBER_REQUIRED", "至少需要一个启用成员");
        }
        for (ChannelPoolRequest.Member member : members) {
            Channel channel = channels.findById(member.channelId())
                    .orElseThrow(() -> new BusinessException("CHANNEL_POOL_MEMBER_NOT_FOUND", "通道池成员不存在"));
            if (member.enabled()) {
                var decision = eligibility.evaluate(channel);
                if (!decision.eligible()) {
                    throw new BusinessException("CHANNEL_POOL_MEMBER_INELIGIBLE", decision.reasonCode());
                }
            }
        }
        if ("WEIGHTED".equals(mode)) {
            int total = members.stream().filter(ChannelPoolRequest.Member::enabled).mapToInt(ChannelPoolRequest.Member::weight).sum();
            boolean invalid = members.stream().filter(ChannelPoolRequest.Member::enabled).anyMatch(member -> member.weight() <= 0);
            if (invalid || total != 100) {
                throw new BusinessException("CHANNEL_POOL_WEIGHT_INVALID", "权重池启用成员权重合计必须为100且均为正数");
            }
        } else {
            long primaries = members.stream().filter(ChannelPoolRequest.Member::enabled).filter(ChannelPoolRequest.Member::primaryMember).count();
            if (primaries != 1) {
                throw new BusinessException("CHANNEL_POOL_PRIMARY_INVALID", "主备池必须且只能有一个主通道");
            }
        }
        return new Validated(name, mode, members);
    }

    private void replaceMembers(long poolId, List<ChannelPoolRequest.Member> members) {
        jdbc.update("DELETE FROM channel_pool_members WHERE pool_id=?", poolId);
        for (ChannelPoolRequest.Member member : members) {
            jdbc.update("""
                    INSERT INTO channel_pool_members(pool_id, channel_id, weight, primary_member, enabled)
                    VALUES (?, ?, ?, ?, ?)
                    """, poolId, member.channelId(), member.weight(), member.primaryMember(), member.enabled());
        }
    }

    private static String required(String value, String code, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private record Validated(String name, String mode, List<ChannelPoolRequest.Member> members) { }
}
