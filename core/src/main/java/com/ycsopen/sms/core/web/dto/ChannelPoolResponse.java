package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record ChannelPoolResponse(long id, String name, String mode, long version, String status, List<Member> members) {
    public record Member(long channelId, int weight, boolean primaryMember, boolean enabled) { }
}
