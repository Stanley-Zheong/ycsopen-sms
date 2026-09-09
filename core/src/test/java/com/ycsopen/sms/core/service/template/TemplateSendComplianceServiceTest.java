package com.ycsopen.sms.core.service.template;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Signature;
import com.ycsopen.sms.core.domain.entity.Template;
import com.ycsopen.sms.core.repository.SignatureRepository;
import com.ycsopen.sms.core.repository.TemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateSendComplianceServiceTest {

    @Test
    void validatesApprovedTenantOwnedTemplateSignatureBindingAndExactVariables() {
        TemplateRepository templates = mock(TemplateRepository.class);
        SignatureRepository signatures = mock(SignatureRepository.class);
        TemplateSendComplianceService service = new TemplateSendComplianceService(templates, signatures);
        when(templates.findById(8L)).thenReturn(Optional.of(template(17L, 9L, "您的验证码是 ${code}",
                "code:digits(4-8)", "APPROVED", false)));
        when(signatures.findById(9L)).thenReturn(Optional.of(signature(17L, "安全签名", "APPROVED")));

        var result = service.validateDomesticSend(17L, "8", "9", Map.of("code", "2468"));

        assertThat(result.finalContent()).isEqualTo("【安全签名】您的验证码是 2468");
        assertThat(result.template().getId()).isEqualTo(8L);
        assertThat(result.signature().getId()).isEqualTo(9L);
    }

    @Test
    void rejectsDomesticFreeTextUnapprovedResourcesWrongOwnershipBindingAndVariableMismatch() {
        TemplateRepository templates = mock(TemplateRepository.class);
        SignatureRepository signatures = mock(SignatureRepository.class);
        TemplateSendComplianceService service = new TemplateSendComplianceService(templates, signatures);

        assertThatThrownBy(() -> service.validateDomesticSend(17L, null, "9", Map.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DOMESTIC_TEMPLATE_REQUIRED"));

        when(templates.findById(8L)).thenReturn(Optional.of(template(18L, 9L, "您的验证码是 ${code}",
                "code:digits(4-8)", "APPROVED", false)));
        assertThatThrownBy(() -> service.validateDomesticSend(17L, "8", "9", Map.of("code", "2468")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_NOT_OWNED"));

        when(templates.findById(8L)).thenReturn(Optional.of(template(17L, 9L, "您的验证码是 ${code}",
                "code:digits(4-8)", "APPROVED", false)));
        when(signatures.findById(9L)).thenReturn(Optional.of(signature(17L, "安全签名", "REJECTED")));
        assertThatThrownBy(() -> service.validateDomesticSend(17L, "8", "9", Map.of("code", "2468")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_NOT_APPROVED"));

        when(signatures.findById(9L)).thenReturn(Optional.of(signature(17L, "安全签名", "APPROVED")));
        assertThatThrownBy(() -> service.validateDomesticSend(17L, "8", "10", Map.of("code", "2468")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_SIGNATURE_BINDING_INVALID"));
        assertThatThrownBy(() -> service.validateDomesticSend(17L, "8", "9", Map.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_VARIABLE_MISSING"));
        assertThatThrownBy(() -> service.validateDomesticSend(17L, "8", "9", Map.of("code", "2468", "extra", "x")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_VARIABLE_EXTRA"));
    }

    private static Template template(long tenantId, long signatureId, String content, String rule,
                                     String status, boolean system) {
        Template template = new Template();
        template.setId(8L);
        template.setTenantId(tenantId);
        template.setSignatureId(signatureId);
        template.setContent(content);
        template.setParamCheckRule(rule);
        template.setAuditStatus(Template.AuditStatus.valueOf(status));
        template.setIsSystemTemplate(system);
        return template;
    }

    private static Signature signature(long tenantId, String content, String status) {
        Signature signature = new Signature();
        signature.setId(9L);
        signature.setTenantId(tenantId);
        signature.setSignContent(content);
        signature.setAuditStatus(Signature.AuditStatus.valueOf(status));
        return signature;
    }
}
