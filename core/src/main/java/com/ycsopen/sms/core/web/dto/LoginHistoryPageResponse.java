package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record LoginHistoryPageResponse(
        List<LoginHistoryItemResponse> items,
        int page,
        int size,
        long totalElements) {

    public LoginHistoryPageResponse {
        items = List.copyOf(items);
    }
}
