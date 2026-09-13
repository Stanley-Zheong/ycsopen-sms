package com.ycsopen.sms.core.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** F-6.2 external HMAC batch send request. Each item keeps its own submitId for idempotency. */
public record SmsBatchSendRequest(
        @NotEmpty @Size(max = 500) List<@Valid SmsSendRequest> messages
) { }
