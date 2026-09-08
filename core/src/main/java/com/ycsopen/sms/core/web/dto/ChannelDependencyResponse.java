package com.ycsopen.sms.core.web.dto;

public record ChannelDependencyResponse(String source, String referenceId, String state,
                                        boolean unresolved, Long destinationChannelId) {
}
