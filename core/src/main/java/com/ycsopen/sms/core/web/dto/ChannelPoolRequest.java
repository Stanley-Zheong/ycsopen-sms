package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record ChannelPoolRequest(String name, String mode, Long expectedVersion, List<Member> members) {
    public record Member(long channelId, int weight, boolean primaryMember, boolean enabled) { }
}
