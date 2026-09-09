package com.ycsopen.sms.core.service.channel.health;

import com.ycsopen.sms.core.domain.entity.Channel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelCandidateEligibilityServiceTest {
    private final ChannelCandidateEligibilityService service = new ChannelCandidateEligibilityService();

    @Test
    void onlyNormalAvailableEffectiveChannelsAreEligible() {
        Channel channel = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);

        assertThat(service.evaluate(channel))
                .extracting(ChannelCandidateEligibilityService.Decision::eligible,
                        ChannelCandidateEligibilityService.Decision::reasonCode)
                .containsExactly(true, "ELIGIBLE");
    }

    @Test
    void rejectsPausedMaintenanceAbnormalOfflineUnavailableAndNonEffectiveChannelsWithReason() {
        Channel paused = ChannelHealthTestSupport.channel(1L, Channel.Status.PAUSED);
        Channel maintenance = ChannelHealthTestSupport.channel(2L, Channel.Status.MAINTENANCE);
        Channel abnormal = ChannelHealthTestSupport.channel(3L, Channel.Status.ABNORMAL);
        Channel offline = ChannelHealthTestSupport.channel(4L, Channel.Status.OFFLINE);
        Channel unavailable = ChannelHealthTestSupport.channel(5L, Channel.Status.NORMAL);
        unavailable.setAvailability("DISABLED");
        Channel draftOnly = ChannelHealthTestSupport.channel(6L, Channel.Status.NORMAL);
        draftOnly.setEffectiveVersionId(null);

        assertThat(service.evaluate(paused).reasonCode()).isEqualTo("STATUS_PAUSED");
        assertThat(service.evaluate(maintenance).reasonCode()).isEqualTo("STATUS_MAINTENANCE");
        assertThat(service.evaluate(abnormal).reasonCode()).isEqualTo("STATUS_ABNORMAL");
        assertThat(service.evaluate(offline).reasonCode()).isEqualTo("STATUS_OFFLINE");
        assertThat(service.evaluate(unavailable).reasonCode()).isEqualTo("AVAILABILITY_DISABLED");
        assertThat(service.evaluate(draftOnly).reasonCode()).isEqualTo("NO_EFFECTIVE_VERSION");
    }
}
