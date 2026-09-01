package com.ycsopen.sms.core.service.message;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageMobile;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.domain.entity.Signature;
import com.ycsopen.sms.core.domain.entity.Template;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import com.ycsopen.sms.core.repository.SignatureRepository;
import com.ycsopen.sms.core.repository.TemplateRepository;
import com.ycsopen.sms.core.service.billing.BillingService;
import com.ycsopen.sms.core.service.routing.RoutingContext;
import com.ycsopen.sms.core.service.routing.RoutingDecision;
import com.ycsopen.sms.core.service.routing.RoutingEngine;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageSubmitServiceTest {

    private static final long TENANT_ID = 17L;
    private static final String MOBILE = "13800138000";
    private static final VersionedBlindIndex RETIRING_INDEX =
            new VersionedBlindIndex(1, repeatedBytes(0x11));
    private static final VersionedBlindIndex ACTIVE_INDEX =
            new VersionedBlindIndex(2, repeatedBytes(0x22));
    private static final BlindIndexPort.OrderedIndexes QUERY_INDEXES =
            new BlindIndexPort.OrderedIndexes(List.of(RETIRING_INDEX, ACTIVE_INDEX));

    @Mock TemplateRepository templateRepository;
    @Mock SignatureRepository signatureRepository;
    @Mock RoutingEngine routingEngine;
    @Mock BillingService billingService;
    @Mock MessageTaskProtectionAdapter messageTaskProtectionAdapter;
    @Mock MessageTaskRepository legacyMessageTaskRepository;
    @Mock PreparedMessageMobile preparedMobile;

    private MessageSubmitService service;

    @BeforeEach
    void setUp() {
        service = new MessageSubmitService(templateRepository, signatureRepository,
                routingEngine, billingService, messageTaskProtectionAdapter);
        approvedTemplateAndSignature();
    }

    @Test
    void preparesBeforeRoutingAndSavesOnlyThroughProtectedAdapter() throws Exception {
        stubPreparedQueryIndexes();
        when(messageTaskProtectionAdapter.prepare(eq(TENANT_ID), anyString(), eq(MOBILE)))
                .thenReturn(preparedMobile);
        when(routingEngine.route(any())).thenReturn(RoutingDecision.allow(42L, "【安全签名】你的验证码是 2468"));
        when(messageTaskProtectionAdapter.save(any(), same(preparedMobile))).thenAnswer(invocation -> {
            MessageTask task = invocation.getArgument(0);
            task.setId(91L);
            return task;
        });

        var response = service.submit(TENANT_ID, request(), "127.0.0.1");

        ArgumentCaptor<String> messageId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RoutingContext> routing = ArgumentCaptor.forClass(RoutingContext.class);
        ArgumentCaptor<MessageTask> task = ArgumentCaptor.forClass(MessageTask.class);
        InOrder order = inOrder(messageTaskProtectionAdapter, routingEngine, billingService);
        order.verify(messageTaskProtectionAdapter).prepare(eq(TENANT_ID), messageId.capture(), eq(MOBILE));
        order.verify(routingEngine).route(routing.capture());
        order.verify(messageTaskProtectionAdapter).save(task.capture(), same(preparedMobile));
        order.verify(billingService).reserve(TENANT_ID, 91L, new BigDecimal("0.05"));

        assertThat(messageId.getValue()).matches("MSG_[0-9]{1,19}_[A-Z0-9]{8}");
        assertThat(task.getValue().getMessageId()).isEqualTo(messageId.getValue());
        assertThat(task.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(task.getValue().hasPreparedMobile()).isFalse();
        assertThat(response.messageId()).isEqualTo(messageId.getValue());
        assertThat(response.status()).isEqualTo(MessageTask.SendStatus.PENDING.name());

        RoutingContext context = routing.getValue();
        assertThat(context.getMobileQueryIndexes()).isEqualTo(QUERY_INDEXES);
        assertThat(context.getMobileHash()).isEqualTo(ACTIVE_INDEX.canonicalValue());
        assertThat(context.getMobileQueryIndexes().values())
                .extracting(VersionedBlindIndex::canonicalValue)
                .doesNotContain(MOBILE, rawMobileSha256());
        verifyNoInteractions(legacyMessageTaskRepository);
    }

    @Test
    void routingRejectionDoesNotPersistTaskOrReserveBilling() {
        stubPreparedQueryIndexes();
        when(messageTaskProtectionAdapter.prepare(eq(TENANT_ID), anyString(), eq(MOBILE)))
                .thenReturn(preparedMobile);
        when(routingEngine.route(any())).thenReturn(RoutingDecision.reject(
                RoutingDecision.RejectStage.BLACKLIST, "blocked"));

        assertThatThrownBy(() -> service.submit(TENANT_ID, request(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked");

        verify(messageTaskProtectionAdapter, never()).save(any(), any());
        verifyNoInteractions(billingService, legacyMessageTaskRepository);
    }

    @Test
    void protectionDependencyFailureStopsBeforeRoutingAndEveryWrite() {
        when(messageTaskProtectionAdapter.prepare(eq(TENANT_ID), anyString(), eq(MOBILE)))
                .thenThrow(new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE));

        assertThatThrownBy(() -> service.submit(TENANT_ID, request(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MessageTaskProtectionAdapter.SANITIZED_FAILURE);

        verifyNoInteractions(routingEngine, billingService, legacyMessageTaskRepository);
        verify(messageTaskProtectionAdapter, never()).save(any(), any());
    }

    @Test
    void protectedSaveFailureDoesNotContinueToBillingOrLegacyRepository() {
        stubPreparedQueryIndexes();
        when(messageTaskProtectionAdapter.prepare(eq(TENANT_ID), anyString(), eq(MOBILE)))
                .thenReturn(preparedMobile);
        when(routingEngine.route(any())).thenReturn(RoutingDecision.allow(42L, "safe content"));
        when(messageTaskProtectionAdapter.save(any(), same(preparedMobile)))
                .thenThrow(new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE));

        assertThatThrownBy(() -> service.submit(TENANT_ID, request(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MessageTaskProtectionAdapter.SANITIZED_FAILURE);

        verifyNoInteractions(billingService, legacyMessageTaskRepository);
    }

    private void approvedTemplateAndSignature() {
        Template template = new Template();
        template.setId(8L);
        template.setTenantId(TENANT_ID);
        template.setSignatureId(9L);
        template.setContent("你的验证码是 ${code}");
        template.setIsSystemTemplate(false);
        template.setAuditStatus(Template.AuditStatus.APPROVED);
        Signature signature = new Signature();
        signature.setId(9L);
        signature.setSignContent("安全签名");
        signature.setAuditStatus(Signature.AuditStatus.APPROVED);
        when(templateRepository.findById(8L)).thenReturn(Optional.of(template));
        when(signatureRepository.findById(9L)).thenReturn(Optional.of(signature));
    }

    private static SmsSendRequest request() {
        return new SmsSendRequest(MOBILE, "8", null, Map.of("code", "2468"), null);
    }

    private void stubPreparedQueryIndexes() {
        when(preparedMobile.queryIndexes()).thenReturn(QUERY_INDEXES);
    }

    private static byte[] repeatedBytes(int value) {
        byte[] bytes = new byte[VersionedBlindIndex.HMAC_BYTES];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static String rawMobileSha256() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(MOBILE.getBytes(StandardCharsets.US_ASCII)));
    }
}
