package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record ChannelDependencyMigrationRequest(List<Item> items) {
    public record Item(String source, String referenceId, Long destinationChannelId) { }
}
