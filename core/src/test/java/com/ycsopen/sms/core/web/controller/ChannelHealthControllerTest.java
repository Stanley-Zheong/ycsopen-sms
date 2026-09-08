package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.service.channel.health.ChannelCandidateEligibilityService;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthService;
import com.ycsopen.sms.core.service.channel.health.ChannelPoolService;
import com.ycsopen.sms.core.web.dto.ChannelHealthMonitorResponse;
import com.ycsopen.sms.core.web.dto.ChannelPauseRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelHealthControllerTest {

    @Test
    void pauseUsesAuthenticatedActorInsteadOfClientSuppliedActor() {
        ChannelHealthService health = mock(ChannelHealthService.class);
        ChannelHealthController controller = new ChannelHealthController(health, mock(ChannelPoolService.class),
                new ChannelCandidateEligibilityService(), mock(ChannelRepository.class));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("42");
        when(health.pause(org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row());

        controller.pause(11L, new ChannelPauseRequest("MANUAL", "spoofed-user", "manual hold"), authentication);

        ArgumentCaptor<ChannelPauseRequest> captor = ArgumentCaptor.forClass(ChannelPauseRequest.class);
        verify(health).pause(org.mockito.ArgumentMatchers.eq(11L), captor.capture());
        assertThat(captor.getValue().actor()).isEqualTo("42");
        assertThat(captor.getValue().reason()).isEqualTo("manual hold");
    }

    private static ChannelHealthMonitorResponse row() {
        return new ChannelHealthMonitorResponse(11L, "channel-11", "CMPP", "MOBILE", "PAUSED",
                "PAUSED", BigDecimal.ZERO, BigDecimal.ZERO, 1L, "OK", false,
                "STATUS_PAUSED", 1L, "manual hold", "42", null);
    }
}
