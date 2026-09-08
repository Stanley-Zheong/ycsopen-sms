package com.ycsopen.sms.core.web.dto;

public record ChannelConnectivityResponse(boolean passed, String reasonCode, boolean retryable) {
}
