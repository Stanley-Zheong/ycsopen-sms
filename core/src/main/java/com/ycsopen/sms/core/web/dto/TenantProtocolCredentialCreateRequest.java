package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.*;

public record TenantProtocolCredentialCreateRequest(@NotBlank @Size(max=32) String spid,
        @NotBlank @Size(max=255) String endpointHost, @NotNull @Min(1) @Max(65535) Integer endpointPort,
        @NotBlank @Size(max=100) String account, @NotBlank @Size(min=8,max=100) String password,
        String ipWhitelist, @NotNull @Positive @Max(1000) Integer maxConnections,
        @NotNull @Positive @Max(100000) Integer tpsLimit,
        @NotNull @Positive @Max(10000) Integer windowSize) { }
