package com.ycsopen.sms.core.service.channel.health;

import com.ycsopen.sms.core.domain.entity.Channel;
import org.springframework.stereotype.Service;

/** Single Phase11 fence for deciding whether a channel can receive new work. */
@Service
public class ChannelCandidateEligibilityService {

    public Decision evaluate(Channel channel) {
        if (channel == null) {
            return Decision.ineligible("CHANNEL_MISSING");
        }
        if (channel.getStatus() != Channel.Status.NORMAL) {
            return Decision.ineligible("STATUS_" + channel.getStatus().name());
        }
        if (!"AVAILABLE".equals(channel.getAvailability())) {
            return Decision.ineligible("AVAILABILITY_" + channel.getAvailability());
        }
        if (channel.getEffectiveVersionId() == null) {
            return Decision.ineligible("NO_EFFECTIVE_VERSION");
        }
        return new Decision(true, "ELIGIBLE");
    }

    public record Decision(boolean eligible, String reasonCode) {
        static Decision ineligible(String reasonCode) {
            return new Decision(false, reasonCode);
        }
    }
}
