package com.ycsopen.sms.core.web.dto;

public record ChannelCandidateResponse(long channelId, boolean eligible, String reasonCode) { }
