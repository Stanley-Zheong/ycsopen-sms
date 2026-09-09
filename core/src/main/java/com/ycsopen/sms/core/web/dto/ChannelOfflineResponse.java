package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record ChannelOfflineResponse(long channelId, String status, boolean changed,
                                     List<ChannelDependencyResponse> unresolvedDependencies) {
}
