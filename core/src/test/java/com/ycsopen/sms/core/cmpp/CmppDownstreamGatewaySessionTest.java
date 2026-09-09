package com.ycsopen.sms.core.cmpp;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CmppDownstreamGatewaySessionTest {
    private static final int TIMESTAMP = 926091234;
    private final Store store = new Store();
    private final Acceptance acceptance = new Acceptance();
    private final CmppDownstreamConnectionRegistry connections = new CmppDownstreamConnectionRegistry();
    private final CmppDownstreamSessionRegistry sessions = new CmppDownstreamSessionRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void permittedClientAuthenticatesAndLifecycleWorks() {
        store.put(credential(1, "10.0.0.7", 2, 10, 2));
        CmppDownstreamGatewaySession session = session();

        CmppPdu connect = session.handle(connect("sp100", "secret"), "10.0.0.7");
        CmppPdu active = session.handle(new CmppPdu(CmppCommands.ACTIVE_TEST, 2, new byte[0]), "10.0.0.7");
        CmppPdu terminate = session.handle(new CmppPdu(CmppCommands.TERMINATE, 3, new byte[0]), "10.0.0.7");

        assertThat(CmppBodyCodec.connectResp(connect.body()).status()).isEqualTo(CmppDownstreamGatewaySession.RESULT_OK);
        assertThat(active.commandId()).isEqualTo(CmppCommands.ACTIVE_TEST_RESP);
        assertThat(terminate.commandId()).isEqualTo(CmppCommands.TERMINATE_RESP);
        assertThat(session.connected()).isFalse();
        assertThat(connections.active(1)).isZero();
    }

    @Test
    void rejectsWrongPasswordDisallowedIpAndConnectionLimitPrecisely() {
        store.put(credential(1, "10.0.0.7", 1, 10, 2));
        CmppDownstreamGatewaySession first = session();
        CmppDownstreamGatewaySession second = session();

        assertThat(connectStatus(first.handle(connect("sp100", "wrong"), "10.0.0.7")))
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_AUTH_FAILED);
        assertThat(connectStatus(first.handle(connect("sp100", "secret"), "10.0.0.9")))
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_IP_DENIED);
        assertThat(connectStatus(first.handle(connect("sp100", "secret"), "10.0.0.7")))
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_OK);
        assertThat(connectStatus(second.handle(connect("sp100", "secret"), "10.0.0.7")))
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_CONNECTION_LIMIT);
    }

    @Test
    void submitReusesAcceptanceBoundaryAndRejectsBadBindingsWithoutChargePath() {
        store.put(credential(1, "10.0.0.7", 1, 10, 2));
        CmppDownstreamGatewaySession session = connectedSession();

        CmppPdu accepted = session.handle(submit(11, "submit-1", "13800138000", "sp100", "TPL_1", true), "10.0.0.7");
        CmppPdu mismatchedService = session.handle(submit(12, "submit-2", "13800138000", "wrong-sp", "TPL_1", true), "10.0.0.7");
        CmppPdu malformed = session.handle(new CmppPdu(CmppCommands.SUBMIT, 13, new byte[] {1, 2}), "10.0.0.7");

        assertThat(CmppBodyCodec.submitResp(accepted.body()).result()).isEqualTo(CmppDownstreamGatewaySession.RESULT_OK);
        assertThat(CmppBodyCodec.submitResp(accepted.body()).providerMessageId()).isEqualTo("MSG-submit-1");
        assertThat(CmppBodyCodec.submitResp(mismatchedService.body()).result())
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_BINDING_INVALID);
        assertThat(CmppBodyCodec.submitResp(malformed.body()).result())
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_MALFORMED);
        assertThat(acceptance.acceptedSubmitIds()).containsOnly("submit-1");
    }

    @Test
    void tpsPolicyAndAcceptanceRejectCreatePreciseSubmitResponses() {
        store.put(credential(1, "10.0.0.7", 1, 1, 2));
        acceptance.reject("submit-reject", "TEMPLATE_REJECTED");
        CmppDownstreamGatewaySession session = connectedSession();

        CmppPdu rejected = session.handle(submit(11, "submit-reject", "13800138000", "sp100", "TPL_BAD", true), "10.0.0.7");
        CmppPdu rateLimited = session.handle(submit(12, "submit-next", "13800138001", "sp100", "TPL_1", true), "10.0.0.7");

        assertThat(CmppBodyCodec.submitResp(rejected.body()).result())
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_ACCEPTANCE_REJECTED);
        assertThat(CmppBodyCodec.submitResp(rateLimited.body()).result())
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_TPS_LIMIT);
        assertThat(acceptance.acceptedSubmitIds()).isEmpty();
    }

    @Test
    void deliveryReportsUseSessionWindowAckAndDurablePendingQueue() {
        store.put(credential(1, "10.0.0.7", 1, 10, 1));
        CmppDownstreamGatewaySession session = connectedSession();
        session.queueReport(91, "UP-1", "MSG-1", "DELIVRD");
        session.queueReport(91, "UP-2", "MSG-2", "DELIVRD");

        var firstDrain = session.drainDeliveries();
        var secondDrain = session.drainDeliveries();
        session.handle(new CmppPdu(CmppCommands.DELIVER_RESP, firstDrain.getFirst().sequenceId(),
                CmppBodyCodec.deliverResp("UP-1", 0)), "10.0.0.7");
        var thirdDrain = session.drainDeliveries();

        assertThat(firstDrain).hasSize(1);
        assertThat(secondDrain).isEmpty();
        assertThat(thirdDrain).hasSize(1);
        assertThat(session.pendingDeliveryCount()).isZero();
        assertThat(session.inflightDeliveryCount()).isEqualTo(1);
    }

    @Test
    void revocationClosesLiveSessionAndRetainsUnackedDelivery() {
        store.put(credential(1, "10.0.0.7", 1, 10, 1));
        CmppDownstreamGatewaySession session = connectedSession();
        session.queueReport(91, "UP-1", "MSG-1", "DELIVRD");
        assertThat(session.drainDeliveries()).hasSize(1);

        session.revokeCredential(1);
        CmppPdu submitAfterRevoke = session.handle(submit(99, "submit-after-revoke", "13800138009", "sp100", "TPL_1", true),
                "10.0.0.7");

        assertThat(session.connected()).isFalse();
        assertThat(session.pendingDeliveryCount()).isEqualTo(1);
        assertThat(CmppBodyCodec.submitResp(submitAfterRevoke.body()).result())
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_NOT_CONNECTED);
        assertThat(connections.active(1)).isZero();
    }

    @Test
    void pendingReportsSurviveReconnectThroughTenantSessionRegistry() {
        store.put(credential(1, "10.0.0.7", 1, 10, 1));
        CmppDownstreamGatewaySession first = connectedSession();
        first.queueReport(91, "UP-1", "MSG-1", "DELIVRD");
        assertThat(first.drainDeliveries()).hasSize(1);
        first.revokeCredential(1);

        CmppDownstreamGatewaySession second = connectedSession();
        var redelivered = second.drainDeliveries();

        assertThat(redelivered).hasSize(1);
        assertThat(CmppBodyCodec.decodeDeliver(redelivered.getFirst().body()).first()).isEqualTo("UP-1");
        assertThat(sessions.hasActiveSession(91)).isTrue();
    }

    private CmppDownstreamGatewaySession connectedSession() {
        CmppDownstreamGatewaySession session = session();
        assertThat(connectStatus(session.handle(connect("sp100", "secret"), "10.0.0.7")))
                .isEqualTo(CmppDownstreamGatewaySession.RESULT_OK);
        return session;
    }

    private CmppDownstreamGatewaySession session() {
        return new CmppDownstreamGatewaySession(store, acceptance, connections, sessions, clock);
    }

    private static CmppPdu connect(String account, String password) {
        return new CmppPdu(CmppCommands.CONNECT, 1, CmppBodyCodec.connect(account,
                CmppAuthenticator.authenticatorSource(account, password, TIMESTAMP), (byte) 0x30, TIMESTAMP));
    }

    private static CmppPdu submit(int sequence, String submitId, String destination, String serviceId,
                                  String templateId, boolean registeredDelivery) {
        return new CmppPdu(CmppCommands.SUBMIT, sequence, CmppDownstreamBodyCodec.submit(
                submitId, destination, serviceId, templateId, "PRODUCT_A", registeredDelivery, "code=1234"));
    }

    private static int connectStatus(CmppPdu pdu) {
        return CmppBodyCodec.connectResp(pdu.body()).status();
    }

    private static CmppDownstreamCredentialStore.Credential credential(long id, String ipWhitelist,
                                                                       int maxConnections, int tpsLimit,
                                                                       int windowSize) {
        return new CmppDownstreamCredentialStore.Credential(
                id, 91, "sp100", "secret", "sp100", ipWhitelist, maxConnections, tpsLimit, windowSize);
    }

    private static final class Store implements CmppDownstreamCredentialStore {
        private final Map<String, Credential> credentials = new LinkedHashMap<>();

        void put(Credential credential) {
            credentials.put(credential.account(), credential);
        }

        @Override
        public Optional<Credential> findActiveByAccount(String account) {
            return Optional.ofNullable(credentials.get(account));
        }
    }

    private static final class Acceptance implements CmppDownstreamAcceptancePort {
        private final Map<String, String> rejects = new LinkedHashMap<>();
        private final java.util.List<String> acceptedSubmitIds = new java.util.ArrayList<>();

        void reject(String submitId, String errorCode) {
            rejects.put(submitId, errorCode);
        }

        java.util.List<String> acceptedSubmitIds() {
            return acceptedSubmitIds;
        }

        @Override
        public AcceptanceResult accept(SubmitCommand command) {
            String errorCode = rejects.get(command.submitId());
            if (errorCode != null) {
                return AcceptanceResult.rejected(errorCode, "rejected by shared acceptance");
            }
            acceptedSubmitIds.add(command.submitId());
            return AcceptanceResult.accepted("MSG-" + command.submitId());
        }
    }
}
