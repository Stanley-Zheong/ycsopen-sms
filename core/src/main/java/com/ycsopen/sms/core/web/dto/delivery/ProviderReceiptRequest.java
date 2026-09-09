package com.ycsopen.sms.core.web.dto.delivery;

import java.time.LocalDateTime;

public record ProviderReceiptRequest(String messageId, String providerMessageId, Long channelId,
                                     String providerStatus, String errorCode, String errorMessage,
                                     String safePayload, LocalDateTime reportTime) { }
