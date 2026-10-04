package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) { }
