package com.ycsopen.sms.core.service.message;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.persistence.LegacyMobileLookupToken;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageRouting;
import com.ycsopen.sms.core.domain.entity.Signature;
import com.ycsopen.sms.core.domain.entity.Template;
import com.ycsopen.sms.core.repository.SignatureRepository;
import com.ycsopen.sms.core.repository.TemplateRepository;
import com.ycsopen.sms.core.service.billing.BillingService;
import com.ycsopen.sms.core.service.billing.FeeWarningCreditService;
import com.ycsopen.sms.core.service.routing.RoutingDecision;
import com.ycsopen.sms.core.service.routing.RoutingEngine;
import com.ycsopen.sms.core.service.template.TemplateSendComplianceService;
import com.ycsopen.sms.core.service.tenant.TenantEligibilityPolicy;
import com.ycsopen.sms.core.service.tool.NumberAttributionService;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageSubmitTransactionIntegrationTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private MessageSubmitService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("issue119-submit-transaction-" + System.nanoTime())
                .build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                CREATE TABLE message_submits(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id VARCHAR(64) NOT NULL,
                  request_digest CHAR(64),
                  source_protocol VARCHAR(16) NOT NULL,
                  product_type VARCHAR(32) NOT NULL,
                  template_id BIGINT,
                  signature_id BIGINT,
                  status VARCHAR(16) NOT NULL,
                  reject_reason VARCHAR(255),
                  UNIQUE(tenant_id, submit_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_submit_claim_leases(
                  submission_id BIGINT PRIMARY KEY,
                  processing_token VARCHAR(36) NOT NULL,
                  lease_expires_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id BIGINT,
                  message_id VARCHAR(64) NOT NULL,
                  send_status VARCHAR(16) NOT NULL
                )
                """);
        jdbc.execute("CREATE TABLE issue119_business_writes(id BIGINT PRIMARY KEY)");

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(database);
        MessageAcceptanceIdempotencyService idempotency = transactionalProxy(
                new MessageAcceptanceIdempotencyService(jdbc), transactionManager,
                MessageAcceptanceIdempotencyService.class);
        MessageRejectionRecorder rejectionRecorder = transactionalProxy(
                new MessageRejectionRecorder(idempotency), transactionManager,
                MessageRejectionRecorder.class);

        TemplateRepository templates = mock(TemplateRepository.class);
        SignatureRepository signatures = mock(SignatureRepository.class);
        Template template = new Template();
        template.setId(8L);
        template.setTenantId(17L);
        template.setSignatureId(9L);
        template.setContent("你的验证码是 ${code}");
        template.setIsSystemTemplate(false);
        template.setAuditStatus(Template.AuditStatus.APPROVED);
        Signature signature = new Signature();
        signature.setId(9L);
        signature.setTenantId(17L);
        signature.setSignContent("安全签名");
        signature.setAuditStatus(Signature.AuditStatus.APPROVED);
        when(templates.findById(8L)).thenReturn(Optional.of(template));
        when(signatures.findById(9L)).thenReturn(Optional.of(signature));

        PreparedMessageRouting prepared = mock(PreparedMessageRouting.class);
        VersionedBlindIndex index = new VersionedBlindIndex(1, new byte[VersionedBlindIndex.HMAC_BYTES]);
        BlindIndexPort.OrderedIndexes indexes = new BlindIndexPort.OrderedIndexes(List.of(index));
        LegacyMobileLookupToken lookupToken = mock(LegacyMobileLookupToken.class);
        when(prepared.queryIndexes()).thenReturn(indexes);
        when(prepared.legacyLookupToken()).thenReturn(lookupToken);
        MessageTaskProtectionAdapter protection = mock(MessageTaskProtectionAdapter.class);
        when(protection.prepareForRouting(eq(17L), anyString(), eq("13800138000")))
                .thenReturn(prepared);

        RoutingEngine routing = mock(RoutingEngine.class);
        when(routing.route(any())).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO issue119_business_writes(id) VALUES (1)");
            return RoutingDecision.reject(RoutingDecision.RejectStage.BLACKLIST, "blocked");
        });

        service = new MessageSubmitService(
                new TemplateSendComplianceService(templates, signatures),
                routing,
                mock(BillingService.class),
                mock(FeeWarningCreditService.class),
                protection,
                mock(TenantEligibilityPolicy.class),
                idempotency,
                mock(NumberAttributionService.class),
                null,
                null);
        service.configureTransactions(transactionManager, rejectionRecorder,
                Duration.ofSeconds(30), Duration.ofMinutes(2));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.shutdown();
        }
    }

    @Test
    void rollsBackBusinessWorkBeforeDurablyRecordingAndReplayingTheRejection() {
        SmsSendRequest request = new SmsSendRequest(
                "ISSUE-119-TX", "13800138000", "8", null,
                Map.of("code", "2468"), null);

        assertThatThrownBy(() -> service.submit(17L, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("ROUTING_REJECTED");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM issue119_business_writes", Integer.class))
                .isZero();
        assertThat(jdbc.queryForMap("""
                SELECT status, reject_reason, template_id, signature_id
                  FROM message_submits WHERE tenant_id=17 AND submit_id='ISSUE-119-TX'
                """))
                .containsEntry("status", "REJECTED")
                .containsEntry("reject_reason", "ROUTING_REJECTED")
                .containsEntry("template_id", 8L)
                .containsEntry("signature_id", 9L);

        assertThatThrownBy(() -> service.submit(17L, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("ROUTING_REJECTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_submits", Integer.class)).isEqualTo(1);
    }

    private static <T> T transactionalProxy(T target, DataSourceTransactionManager transactionManager,
                                             Class<T> type) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        return type.cast(proxyFactory.getProxy());
    }
}
