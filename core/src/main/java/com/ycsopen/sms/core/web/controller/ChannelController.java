package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** F-4.1/F-4.7 通道管理（新增/查询/暂停/恢复）。CRUD 主体走 Spring Data 默认方法，暂停/恢复是唯一的业务动作。 */
@RestController
@RequestMapping("/api/v1/console/channels")
public class ChannelController {

    private final ChannelRepository channelRepository;

    public ChannelController(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @GetMapping
    public ApiResponse<List<Channel>> list() {
        return ApiResponse.ok(channelRepository.findAll());
    }

    @PostMapping
    public ApiResponse<Channel> create(@RequestBody Channel channel) {
        return ApiResponse.ok(channelRepository.save(channel));
    }

    /** F-4.7 通道暂停：立即从路由候选中移除；PRD 要求记录暂停原因/操作人/时间。 */
    @PostMapping("/{id}/pause")
    public ApiResponse<Channel> pause(@PathVariable Long id, @RequestParam String reason, @RequestParam String operatedBy) {
        Channel channel = channelRepository.findById(id).orElseThrow();
        channel.setStatus(Channel.Status.PAUSED);
        channel.setPauseReason(reason);
        channel.setPausedBy(operatedBy);
        channel.setPausedAt(LocalDateTime.now());
        return ApiResponse.ok(channelRepository.save(channel));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<Channel> resume(@PathVariable Long id) {
        Channel channel = channelRepository.findById(id).orElseThrow();
        channel.setStatus(Channel.Status.NORMAL);
        return ApiResponse.ok(channelRepository.save(channel));
    }
}
