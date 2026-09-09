package com.ycsopen.sms.core.service.template;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Signature;
import com.ycsopen.sms.core.domain.entity.Template;
import com.ycsopen.sms.core.repository.SignatureRepository;
import com.ycsopen.sms.core.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/** Shared Phase 13 pre-send validator for domestic template/signature/variable compliance. */
@Service
public class TemplateSendComplianceService {
    private final TemplateRepository templates;
    private final SignatureRepository signatures;

    public TemplateSendComplianceService(TemplateRepository templates, SignatureRepository signatures) {
        this.templates = templates;
        this.signatures = signatures;
    }

    public Result validateDomesticSend(Long tenantId, String templateId, String signId, Map<String, String> params) {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new BusinessException("DOMESTIC_TEMPLATE_REQUIRED", "国内短信必须使用已审核模板");
        }
        Template template = templates.findById(parseId(templateId, "TEMPLATE_ID_INVALID"))
                .orElseThrow(() -> new BusinessException("TEMPLATE_NOT_FOUND", "模板不存在"));
        if (!Boolean.TRUE.equals(template.getIsSystemTemplate()) && !Objects.equals(template.getTenantId(), tenantId)) {
            throw new BusinessException("TEMPLATE_NOT_OWNED", "模板不属于当前机构");
        }
        if (template.getAuditStatus() != Template.AuditStatus.APPROVED) {
            throw new BusinessException("TEMPLATE_NOT_APPROVED", "模板未通过审核，不可用于发送");
        }
        if (signId != null && !signId.trim().isEmpty() && !template.getSignatureId().equals(parseId(signId, "SIGNATURE_ID_INVALID"))) {
            throw new BusinessException("TEMPLATE_SIGNATURE_BINDING_INVALID", "签名与模板绑定不一致");
        }
        Signature signature = signatures.findById(template.getSignatureId())
                .orElseThrow(() -> new BusinessException("SIGNATURE_NOT_FOUND", "签名不存在"));
        if (!Boolean.TRUE.equals(template.getIsSystemTemplate()) && !Objects.equals(signature.getTenantId(), tenantId)) {
            throw new BusinessException("SIGNATURE_NOT_OWNED", "签名不属于当前机构");
        }
        if (signature.getAuditStatus() != Signature.AuditStatus.APPROVED) {
            throw new BusinessException("SIGNATURE_NOT_APPROVED", "签名未通过审核，不可用于发送");
        }
        var variables = TemplateRuleEngine.variables(template.getContent());
        String rendered = TemplateRuleEngine.render(template.getContent(), variables, template.getParamCheckRule(), params);
        return new Result(template, signature, "【" + signature.getSignContent() + "】" + rendered);
    }

    private static long parseId(String value, String code) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException ex) {
            throw new BusinessException(code, "编号不合法");
        }
    }

    public record Result(Template template, Signature signature, String finalContent) { }
}
