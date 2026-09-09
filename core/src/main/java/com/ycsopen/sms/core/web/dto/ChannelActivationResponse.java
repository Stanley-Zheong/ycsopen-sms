package com.ycsopen.sms.core.web.dto;

public record ChannelActivationResponse(long channelId, Long requestedVersion, Long effectiveVersion,
                                        String resultCode, boolean retryable, String safeReason) {
}
