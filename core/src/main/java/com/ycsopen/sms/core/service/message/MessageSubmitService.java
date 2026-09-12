package com.ycsopen.sms.core.service.message;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageMobile;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageRouting;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.domain.entity.Signature;
import com.ycsopen.sms.core.domain.entity.Template;
import com.ycsopen.sms.core.service.billing.BillingService;
import com.ycsopen.sms.core.service.routing.RoutingContext;
import com.ycsopen.sms.core.service.routing.RoutingDecision;
import com.ycsopen.sms.core.service.routing.RoutingEngine;
import com.ycsopen.sms.core.service.routing.FrequencyChecker;
import com.ycsopen.sms.core.service.template.TemplateSendComplianceService;
import com.ycsopen.sms.core.service.tenant.TenantEligibilityPolicy;
import com.ycsopen.sms.core.service.tool.NumberAttributionService;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * F-6.1 HTTP API 单条发送的编排入口，串联"F-3.7 发送前置校验 -&gt; F-5 路由引擎 -&gt; F-8.1 预扣计费
 * -&gt; 落库 message_tasks"整条链路，对应 4.4 节 Task Flow A。
 * <p>这是本仓库里唯一一处把签名/模板校验、路由决策、计费预扣三个子系统接在一起的地方，
 * 刻意保持"薄"：具体规则都下沉在各自的 Service/Checker 里，本类只负责编排顺序与事务边界。</p>
 */
@Service
public class MessageSubmitService {

    private final TemplateSendComplianceService templateCompliance;
    private final RoutingEngine routingEngine;
    private final BillingService billingService;
    private final MessageTaskProtectionAdapter messageTaskProtectionAdapter;
    private final TenantEligibilityPolicy tenantEligibilityPolicy;
    private final MessageAcceptanceIdempotencyService idempotency;
    private final NumberAttributionService numberAttributionService;

    public MessageSubmitService(TemplateSendComplianceService templateCompliance,
                                 RoutingEngine routingEngine,
                                 BillingService billingService,
                                 MessageTaskProtectionAdapter messageTaskProtectionAdapter,
                                 TenantEligibilityPolicy tenantEligibilityPolicy,
                                 MessageAcceptanceIdempotencyService idempotency,
                                 NumberAttributionService numberAttributionService) {
        this.templateCompliance = templateCompliance;
        this.routingEngine = routingEngine;
        this.billingService = billingService;
        this.messageTaskProtectionAdapter = messageTaskProtectionAdapter;
        this.tenantEligibilityPolicy = tenantEligibilityPolicy;
        this.idempotency = idempotency;
        this.numberAttributionService = numberAttributionService;
    }

    /** Backward-compatible constructor for focused unit tests that do not exercise attribution. */
    public MessageSubmitService(TemplateSendComplianceService templateCompliance,
                                 RoutingEngine routingEngine, BillingService billingService,
                                 FeeWarningCreditService feeWarningCreditService,
                                 MessageTaskProtectionAdapter messageTaskProtectionAdapter,
                                 TenantEligibilityPolicy tenantEligibilityPolicy,
                                 MessageAcceptanceIdempotencyService idempotency) {
        this(templateCompliance, routingEngine, billingService, feeWarningCreditService,
                messageTaskProtectionAdapter, tenantEligibilityPolicy, idempotency, null);
    }

    @Transactional
    public SmsSendResponse submit(Long tenantId, SmsSendRequest request, String clientIp) {
        return submit(tenantId, null, request, clientIp);
    }

    @Transactional
    public SmsSendResponse submit(Long tenantId, Long apiKeyId, SmsSendRequest request, String clientIp) {
        tenantEligibilityPolicy.requireNewWorkAllowed(tenantId);
        MessageAcceptanceIdempotencyService.Claim claim = idempotency.claim(
                tenantId, request.submitId(), requestDigest(request));
        if (claim.existingResponse().isPresent()) {
            return claim.existingResponse().get();
        }

        TemplateSendComplianceService.Result compliance = templateCompliance.validateDomesticSend(
                tenantId, request.templateId(), request.signId(), request.templateParams());
        Template template = compliance.template();
        Signature signature = compliance.signature();
        NumberAttributionService.AttributionResult attribution = numberAttributionService == null
                ? null : numberAttributionService.lookup(request.phoneNumber(), false);
        idempotency.attachResources(claim.submissionId(), template.getId(), signature.getId());

        String messageId = "MSG_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PreparedMessageRouting preparedRouting = messageTaskProtectionAdapter.prepareForRouting(
                tenantId, messageId, request.phoneNumber());

        RoutingContext ctx = RoutingContext.builder()
                .tenantId(tenantId)
                .mobileQueryIndexes(preparedRouting.queryIndexes())
                .legacyMobileLookupToken(preparedRouting.legacyLookupToken())
                .clientIp(clientIp)
                .operatorHint(attribution == null ? null : attribution.carrier())
                .content(compliance.finalContent())
                .templateId(template.getId())
                .signatureId(signature.getId())
                .apiKeyId(apiKeyId)
                .build();

        RoutingDecision decision = routingEngine.route(ctx);
        if (!decision.isAllowed()) {
            if (decision.getRejectStage() == RoutingDecision.RejectStage.FREQUENCY_LIMIT
                    && FrequencyChecker.MOBILE_IDENTITY_NOT_READY.equals(decision.getRejectReason())) {
                throw new BusinessException(FrequencyChecker.MOBILE_IDENTITY_NOT_READY,
                        FrequencyChecker.MOBILE_IDENTITY_NOT_READY);
            }
            throw new BusinessException("ROUTING_REJECTED",
                    "提交被拒绝[%s]：%s".formatted(decision.getRejectStage(), decision.getRejectReason()));
        }

        PreparedMessageMobile preparedMobile = messageTaskProtectionAdapter.protectForPersistence(
                preparedRouting, request.phoneNumber());

        MessageTask task = new MessageTask();
        task.setMessageId(messageId);
        task.setTenantId(tenantId);
        task.setSubmitId(claim.submissionId());
        task.setTemplateId(template.getId());
        task.setSignatureId(signature.getId());
        task.setContent(decision.getFinalContent());
        task.setSendStatus(MessageTask.SendStatus.PENDING);
        task.setChannelId(decision.getSelectedChannelId());
        MessageTask savedTask = messageTaskProtectionAdapter.save(task, preparedMobile);

        // F-8.1 预扣：通道单价从 Channel 读取，此处简化为固定演示单价；生产实现应查 Channel.price。
        billingService.reserve(tenantId, savedTask.getId(), new java.math.BigDecimal("0.05"));
        idempotency.enqueueSendIntent(tenantId, savedTask.getId(), messageId, decision.getSelectedChannelId());
        idempotency.markAccepted(claim.submissionId());

        return new SmsSendResponse(messageId, task.getSendStatus().name());
    }

    private static String requestDigest(SmsSendRequest request) {
        try {
            StringBuilder canonical = new StringBuilder()
                    .append(request.submitId()).append('\n')
                    .append(request.phoneNumber()).append('\n')
                    .append(request.templateId()).append('\n')
                    .append(request.signId() == null ? "" : request.signId()).append('\n')
                    .append(request.callbackUrl() == null ? "" : request.callbackUrl()).append('\n');
            Map<String, String> params = request.templateParams() == null
                    ? Map.of() : new TreeMap<>(request.templateParams());
            params.forEach((key, value) -> canonical.append(key).append('=').append(value).append('\n'));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception impossible) {
            throw new IllegalStateException("REQUEST_DIGEST_UNAVAILABLE", impossible);
        }
    }

}
