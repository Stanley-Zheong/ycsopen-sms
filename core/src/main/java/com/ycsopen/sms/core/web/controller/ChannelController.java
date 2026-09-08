package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.ChannelPauseRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/** F-4.1/F-4.7 通道管理（新增/查询/暂停/恢复）。CRUD 主体走 Spring Data 默认方法，暂停/恢复是唯一的业务动作。 */
@RestController
@RequestMapping("/api/v1/console/channels")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ChannelController {

    private final ChannelRepository channelRepository;
    private final ChannelHealthService channelHealthService;

    public ChannelController(ChannelRepository channelRepository, ChannelHealthService channelHealthService) {
        this.channelRepository = channelRepository;
        this.channelHealthService = channelHealthService;
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
    public ApiResponse<Channel> pause(@PathVariable Long id, @RequestParam String reason,
                                      @RequestParam String operatedBy, Authentication authentication) {
        channelHealthService.pause(id, new ChannelPauseRequest("MANUAL", authentication.getName(), reason));
        Channel channel = channelRepository.findById(id).orElseThrow();
        return ApiResponse.ok(channel);
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<Channel> resume(@PathVariable Long id, Authentication authentication) {
        channelHealthService.resume(id, authentication.getName());
        return ApiResponse.ok(channelRepository.findById(id).orElseThrow());
    }
}
