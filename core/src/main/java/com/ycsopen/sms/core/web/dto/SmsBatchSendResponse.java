package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record SmsBatchSendResponse(List<Item> items) {
    public record Item(String submitId, String messageId, String status, String error) { }
}
