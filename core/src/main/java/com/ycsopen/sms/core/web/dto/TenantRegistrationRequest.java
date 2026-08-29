package com.ycsopen.sms.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** F-2.1 机构资质认证提交表单，字段与 ycsansms.md 5.2 节"页面元素/字段规则"一一对应。 */
public record TenantRegistrationRequest(
        @NotBlank @Size(max = 20) String shortName,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(min = 18, max = 18) String unifiedSocialCreditCode,
        @NotBlank String businessLicenseUrl,
        @NotBlank String legalRepName,
        @NotBlank String legalRepIdNo,
        @NotBlank String legalRepIdFrontUrl,
        @NotBlank String legalRepIdBackUrl,
        @NotBlank String contactName,
        @NotBlank String contactIdNo,
        @NotBlank String contactPhone,
        String shortlinkDomainProofUrl,   // 试用阶段可选，签约前必填 —— 校验放在签约环节 (F-2.9)
        String trademarkProofUrl          // 签名类型为"商标"时必填 —— 校验放在签名申请环节 (F-3.1)
) {
}
