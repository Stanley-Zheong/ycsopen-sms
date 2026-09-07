package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;

public record PlatformAccountCreateRequest(
        @NotBlank @Size(max = 20) String username,
        @NotBlank @Size(max = 72) String password,
        @NotBlank String phone,
        @Size(max = 100) String email,
        @NotBlank @Size(max = 50) String realName,
        @NotBlank String userType,
        LocalDate validUntil,
        @NotEmpty List<Long> roleIds) {
}
