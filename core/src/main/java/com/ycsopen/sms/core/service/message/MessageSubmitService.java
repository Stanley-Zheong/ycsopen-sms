package com.ycsopen.sms.core.service.message;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageMobile;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.domain.entity.Signature;
import com.ycsopen.sms.core.domain.entity.Template;
import com.ycsopen.sms.core.repository.SignatureRepository;
import com.ycsopen.sms.core.repository.TemplateRepository;
import com.ycsopen.sms.core.service.billing.BillingService;
import com.ycsopen.sms.core.service.routing.RoutingContext;
import com.ycsopen.sms.core.service.routing.RoutingDecision;
import com.ycsopen.sms.core.service.routing.RoutingEngine;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F-6.1 HTTP API 单条发送的编排入口，串联"F-3.7 发送前置校验 -&gt; F-5 路由引擎 -&gt; F-8.1 预扣计费
 * -&gt; 落库 message_tasks"整条链路，对应 4.4 节 Task Flow A。
 * <p>这是本仓库里唯一一处把签名/模板校验、路由决策、计费预扣三个子系统接在一起的地方，
 * 刻意保持"薄"：具体规则都下沉在各自的 Service/Checker 里，本类只负责编排顺序与事务边界。</p>
 */
@Service
public class MessageSubmitService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final TemplateRepository templateRepository;
    private final SignatureRepository signatureRepository;
    private final RoutingEngine routingEngine;
    private final BillingService billingService;
    private final MessageTaskProtectionAdapter messageTaskProtectionAdapter;

    public MessageSubmitService(TemplateRepository templateRepository,
                                 SignatureRepository signatureRepository,
                                 RoutingEngine routingEngine,
                                 BillingService billingService,
                                 MessageTaskProtectionAdapter messageTaskProtectionAdapter) {
        this.templateRepository = templateRepository;
        this.signatureRepository = signatureRepository;
        this.routingEngine = routingEngine;
        this.billingService = billingService;
        this.messageTaskProtectionAdapter = messageTaskProtectionAdapter;
    }

    @Transactional
    public SmsSendResponse submit(Long tenantId, SmsSendRequest request, String clientIp) {
        Template template = templateRepository.findById(Long.valueOf(request.templateId()))
                .orElseThrow(() -> new BusinessException("TEMPLATE_NOT_FOUND", "模板不存在"));

        // F-3.7 发送前置校验：必须引用"已通过"的模板；非系统模板还必须属于本机构。
        if (!template.getIsSystemTemplate() && !template.getTenantId().equals(tenantId)) {
            throw new BusinessException("TEMPLATE_NOT_OWNED", "模板不属于当前机构");
        }
        if (template.getAuditStatus() != Template.AuditStatus.APPROVED) {
            throw new BusinessException("TEMPLATE_NOT_APPROVED", "模板未通过审核，不可用于发送");
        }

        Signature signature = signatureRepository.findById(template.getSignatureId())
                .orElseThrow(() -> new BusinessException("SIGNATURE_NOT_FOUND", "签名不存在"));
        if (signature.getAuditStatus() != Signature.AuditStatus.APPROVED) {
            throw new BusinessException("SIGNATURE_NOT_APPROVED", "签名未通过审核，不可用于发送");
        }

        // 渲染最终文本：签名 + 模板内容替换变量。内容审核必须扫描这个"最终文本"而不是模板原文——
        // 直接对应 PRD 检视 Finding #2，是本类存在这段渲染逻辑而不是把模板原文丢给路由引擎的原因。
        String finalContent = renderContent(signature.getSignContent(), template.getContent(), request.templateParams());

        String messageId = "MSG_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PreparedMessageMobile preparedMobile = messageTaskProtectionAdapter.prepare(
                tenantId, messageId, request.phoneNumber());

        RoutingContext ctx = RoutingContext.builder()
                .tenantId(tenantId)
                .mobileQueryIndexes(preparedMobile.queryIndexes())
                .clientIp(clientIp)
                .content(finalContent)
                .templateId(template.getId())
                .signatureId(signature.getId())
                .build();

        RoutingDecision decision = routingEngine.route(ctx);
        if (!decision.isAllowed()) {
            throw new BusinessException("ROUTING_REJECTED",
                    "提交被拒绝[%s]：%s".formatted(decision.getRejectStage(), decision.getRejectReason()));
        }

        MessageTask task = new MessageTask();
        task.setMessageId(messageId);
        task.setTenantId(tenantId);
        task.setTemplateId(template.getId());
        task.setSignatureId(signature.getId());
        task.setContent(decision.getFinalContent());
        task.setSendStatus(MessageTask.SendStatus.PENDING);
        task.setChannelId(decision.getSelectedChannelId());
        MessageTask savedTask = messageTaskProtectionAdapter.save(task, preparedMobile);

        // F-8.1 预扣：通道单价从 Channel 读取，此处简化为固定演示单价；生产实现应查 Channel.price。
        billingService.reserve(tenantId, savedTask.getId(), new java.math.BigDecimal("0.05"));

        // TODO(F-6.7/CMPP + 上游 HTTP 连接器): 真正把消息投递给 decision.getSelectedChannelId()
        // 对应的上游通道——这是当前仓库里最大的一块"占位而非实现"，见 core/docs/ROADMAP.md。

        return new SmsSendResponse(messageId, task.getSendStatus().name());
    }

    private String renderContent(String signContent, String templateContent, Map<String, String> params) {
        String rendered = templateContent;
        if (params != null) {
            Matcher matcher = VARIABLE_PATTERN.matcher(templateContent);
            while (matcher.find()) {
                String var = matcher.group(1);
                String value = params.getOrDefault(var, "");
                rendered = rendered.replace("${" + var + "}", value);
            }
        }
        return "【" + signContent + "】" + rendered;
    }
}
