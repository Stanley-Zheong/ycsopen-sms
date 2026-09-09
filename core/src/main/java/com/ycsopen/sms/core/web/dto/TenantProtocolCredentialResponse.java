package com.ycsopen.sms.core.web.dto;

public record TenantProtocolCredentialResponse(long id, String protocol, String account,
        String spid, String endpointHost, int endpointPort, int maxConnections, int tpsLimit,
        int windowSize, String ipWhitelist, String status, String password) { }
