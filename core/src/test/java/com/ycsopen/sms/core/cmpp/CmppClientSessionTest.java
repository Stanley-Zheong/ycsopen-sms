package com.ycsopen.sms.core.cmpp;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.delivery.SmsUpstreamProviderClient;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmppClientSessionTest {
    @Test
    void connectsAuthenticatesHeartbeatsSubmitsAndTerminatesAgainstSimulator() {
        CmppAuthoritativeSimulator gateway = new CmppAuthoritativeSimulator("900001", "secret");
        CmppClientSession session = session(4);

        session.connect(gateway, 926091234);
        assertThat(session.activeTest(gateway)).isTrue();
        var result = session.submit(gateway, command("idem-1", "MSG_1_AAAAAAAA", "hello"));
        session.terminate(gateway);

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.providerMessageId()).startsWith("CMPP-");
        assertThat(gateway.acceptedLogicalMessages()).isEqualTo(1);
    }

    @Test
    void connectRejectsInvalidAuthenticator() {
        CmppAuthoritativeSimulator gateway = new CmppAuthoritativeSimulator("900001", "secret");
        CmppClientSession session = new CmppClientSession(new CmppClientSession.Config(
                "sim-cmpp", "900001", "wrong-secret", (byte) 0x30, 4,
                new CmppClientSession.ReconnectPolicy(List.of(Duration.ofMillis(10)))), taxonomy());

        assertThatThrownBy(() -> session.connect(gateway, 926091234))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("鉴权");
    }

    @Test
    void upstreamSubmitRejectionStaysRejectedAndCached() {
        CmppAuthoritativeSimulator gateway = new CmppAuthoritativeSimulator("900001", "secret");
        CmppClientSession session = session(4);
        session.connect(gateway, 926091234);
        gateway.rejectSubmit("idem-reject", 8);

        var first = session.submit(gateway, command("idem-reject", "MSG_6_FFFFFFFF", "hello"));
        var second = session.submit(gateway, command("idem-reject", "MSG_6_FFFFFFFF", "hello"));

        assertThat(first.status()).isEqualTo("REJECTED");
        assertThat(first.errorCode()).isEqualTo("CMPP_8");
        assertThat(second).isEqualTo(first);
        assertThat(gateway.submitCount("idem-reject")).isEqualTo(1);
    }

    @Test
    void slowPeerBackpressureDisconnectBackoffAndIdempotentRetryPreserveOwnership() {
        CmppAuthoritativeSimulator gateway = new CmppAuthoritativeSimulator("900001", "secret");
        CmppClientSession session = session(1);
        session.connect(gateway, 926091234);
        gateway.holdSubmitResponses(true);

        assertThatThrownBy(() -> session.submit(gateway, command("idem-slow", "MSG_2_BBBBBBBB", "hello")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知");
        assertThat(session.inflight()).isEqualTo(1);
        assertThatThrownBy(() -> session.submit(gateway, command("idem-slow", "MSG_2_BBBBBBBB", "hello")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知");
        assertThat(gateway.submitCount("idem-slow")).isEqualTo(1);
        assertThatThrownBy(() -> session.submit(gateway, command("idem-next", "MSG_3_CCCCCCCC", "next")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("窗口");

        CmppClientSession.ReconnectPlan plan = session.disconnect();

        assertThat(plan.retainedClaims()).isEqualTo(1);
        assertThat(plan.backoffDelays()).containsExactly(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(40));
        assertThat(gateway.acceptedLogicalMessages()).isEqualTo(1);
    }

    @Test
    void deliveryReportsAndUplinksNormalizeThroughTaxonomyContract() {
        CmppAuthoritativeSimulator gateway = new CmppAuthoritativeSimulator("900001", "secret");
        CmppClientSession session = session(4);
        session.connect(gateway, 926091234);
        var accepted = session.submit(gateway, command("idem-normalize", "MSG_4_DDDDDDDD", "hello"));
        gateway.enqueueReceipt(accepted.providerMessageId(), "MSG_4_DDDDDDDD", "DELIVRD");
        gateway.enqueueUplink("13800138000", "TD");

        List<CmppClientSession.NormalizedEvent> events = session.drainDeliveries(gateway);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).kind()).isEqualTo("RECEIPT");
        assertThat(events.get(0).platformCategory()).isEqualTo("DELIVERED");
        assertThat(events.get(0).finalState()).isTrue();
        assertThat(events.get(0).billable()).isTrue();
        assertThat(events.get(1).kind()).isEqualTo("UPLINK");
        assertThat(events.get(1).sourceAddress()).isEqualTo("13800138000");
    }

    @Test
    void adapterReturnsUnknownInsteadOfDuplicatingWhenCmppOutcomeIsUncertain() {
        CmppAuthoritativeSimulator gateway = new CmppAuthoritativeSimulator("900001", "secret");
        CmppClientSession session = session(1);
        session.connect(gateway, 926091234);
        gateway.holdSubmitResponses(true);
        SmsUpstreamProviderClient adapter = new CmppSmsUpstreamProviderClient(session, gateway);

        var result = adapter.submit(new SmsUpstreamProviderClient.ProviderSubmitRequest(
                42, 91, "MSG_5_EEEEEEEE", 7, "13800138000", "hello", "idem-adapter"));

        assertThat(result.status()).isEqualTo(SmsUpstreamProviderClient.ProviderSubmitResult.Status.UNKNOWN);
        assertThat(gateway.acceptedLogicalMessages()).isEqualTo(1);
        assertThat(session.inflight()).isEqualTo(1);
    }

    @Test
    void adapterRejectsInvalidPermanentInputWithoutRetryingAsUnknown() {
        CmppAuthoritativeSimulator gateway = new CmppAuthoritativeSimulator("900001", "secret");
        CmppClientSession session = session(1);
        session.connect(gateway, 926091234);
        SmsUpstreamProviderClient adapter = new CmppSmsUpstreamProviderClient(session, gateway);

        var result = adapter.submit(new SmsUpstreamProviderClient.ProviderSubmitRequest(
                42, 91, "MSG_7_GGGGGGGG", 7, "not-a-phone", "hello", "idem-invalid"));

        assertThat(result.status()).isEqualTo(SmsUpstreamProviderClient.ProviderSubmitResult.Status.REJECTED);
        assertThat(result.errorCode()).isEqualTo("CMPP_INVALID_REQUEST");
        assertThat(gateway.submitCount("idem-invalid")).isZero();
    }

    private static CmppClientSession session(int windowSize) {
        return new CmppClientSession(new CmppClientSession.Config("sim-cmpp", "900001", "secret", (byte) 0x30,
                windowSize, new CmppClientSession.ReconnectPolicy(List.of(
                Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(40)))), taxonomy());
    }

    private static CmppClientSession.SubmitCommand command(String idempotencyKey, String messageId, String content) {
        return new CmppClientSession.SubmitCommand(idempotencyKey, messageId, "13800138000", content, true);
    }

    private static ProviderStatusTaxonomyPort taxonomy() {
        return (providerName, protocol, providerCode) -> new ProviderStatusTaxonomyPort.NormalizedStatus(
                providerName, protocol, providerCode, "cmpp-test-v1", "DELIVERED",
                true, true, false, "INFO", "none", "test");
    }
}
