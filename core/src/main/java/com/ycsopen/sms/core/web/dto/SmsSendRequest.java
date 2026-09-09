package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** F-6.1 HTTP API 单条发送请求体，字段规则见 ycsansms.md 5.6 节。 */
public record SmsSendRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_.:-]+$", message = "业务流水号格式不正确") String submitId,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phoneNumber,
        @NotBlank String templateId,
        String signId,
        Map<String, String> templateParams,
        @Pattern(regexp = "^https://[^\\s]+$", message = "回调地址必须使用 HTTPS")
        String callbackUrl
) {
}
