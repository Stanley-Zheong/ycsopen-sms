package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantAdministratorUpdateRequest(@NotBlank @Size(max = 50) String realName,
                                                @NotBlank String userType,
                                                @NotBlank String status) { }
