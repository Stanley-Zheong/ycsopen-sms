package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record TenantApiKeyCreateRequest(@NotBlank @Size(max = 64) String name,
                                        @Size(max = 255) String description,
                                        @Future LocalDateTime expireTime,
                                        String ipWhitelist,
                                        @NotNull @Positive Integer perSecond,
                                        @NotNull @Positive Integer perMinute,
                                        @NotNull @Positive Integer perHour,
                                        @NotNull @Positive Integer perDay) { }
