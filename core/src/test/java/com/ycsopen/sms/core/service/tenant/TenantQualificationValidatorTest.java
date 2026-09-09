package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantQualificationValidatorTest {

    private final TenantQualificationValidator validator = new TenantQualificationValidator();

    @Test
    void normalizesCreditCodeAndIdentityBeforeValidationAndDuplicateLookup() {
        TenantRegistrationRequest request = request(" 91350211m000100y46 ", " 11010519491231002x ", null);
        assertThat(request.unifiedSocialCreditCode()).isEqualTo("91350211M000100Y46");
        assertThat(request.legalRepIdNo()).isEqualTo("11010519491231002X");
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void acceptsACompleteRegistrationWithValidChineseCheckDigits() {
        assertThatCode(() -> validator.validate(completeRequest())).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnOtherwiseWellFormedCreditCodeWithWrongCheckDigit() {
        TenantRegistrationRequest request = request("91350211M000100Y47", "11010519491231002X", null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TenantQualificationValidator.ValidationFailure.class)
                .hasMessage("INVALID_UNIFIED_SOCIAL_CREDIT_CODE");
    }

    @Test
    void rejectsAnOtherwiseWellFormedResidentIdentityNumberWithWrongCheckDigit() {
        TenantRegistrationRequest request = request("91350211M000100Y46", "110105194912310021", null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TenantQualificationValidator.ValidationFailure.class)
                .hasMessage("INVALID_RESIDENT_ID");
    }

    @Test
    void requiresTrademarkProofWhenTheRegistrationDeclaresTrademarkUse() {
        TenantRegistrationRequest request = request("91350211M000100Y46", "11010519491231002X", null)
                .withTrademarkUse(true);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TenantQualificationValidator.ValidationFailure.class)
                .hasMessage("TRADEMARK_PROOF_REQUIRED");
    }

    @Test
    void acceptsLegalRepresentativeImagesUpToTheCatalogTenMiBLimit() {
        assertThat(TenantRegistrationObjectSessionService.UploadPurpose.LEGAL_REP_ID_FRONT
                .maximumPlaintextBytes()).isEqualTo(10_485_760L);
        assertThat(TenantRegistrationObjectSessionService.UploadPurpose.LEGAL_REP_ID_BACK
                .maximumPlaintextBytes()).isEqualTo(10_485_760L);
    }

    private static TenantRegistrationRequest completeRequest() {
        return request("91350211M000100Y46", "11010519491231002X", "pobj_v1_trademark");
    }

    private static TenantRegistrationRequest request(String creditCode, String identityNumber,
                                                     String trademarkProof) {
        return new TenantRegistrationRequest("示例机构", "示例机构有限公司", creditCode,
                "12345678-1234-1234-1234-123456789abc", "pobj_v1_license", "张三",
                identityNumber, "pobj_v1_front", "pobj_v1_back", "李四", identityNumber,
                "13800138000", "pobj_v1_domain", trademarkProof,
                false, "100万元人民币", "软件开发", "上海市注册地址1号", "上海市经营地址2号", java.time.LocalDate.of(2099, 12, 31));
    }
}
