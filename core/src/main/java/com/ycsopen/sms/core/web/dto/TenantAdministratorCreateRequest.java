package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantAdministratorCreateRequest(@NotBlank @Size(max = 50) String username,
                                                @NotBlank @Size(min = 8, max = 100) String password,
                                                @NotBlank @Size(max = 50) String realName,
                                                @NotBlank String userType) { }
