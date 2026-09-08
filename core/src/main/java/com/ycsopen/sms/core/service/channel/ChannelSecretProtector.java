package com.ycsopen.sms.core.service.channel;

public interface ChannelSecretProtector {
    byte[] protect(long channelId, String field, char[] value);
}
