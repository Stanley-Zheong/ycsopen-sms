package com.ycsopen.sms.core.cmpp;

import java.security.MessageDigest;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Tenant-facing CMPP gateway session core. Transport is outside this class. */
public final class CmppDownstreamGatewaySession {
    public static final int RESULT_OK = 0;
    public static final int RESULT_AUTH_FAILED = 1;
    public static final int RESULT_IP_DENIED = 2;
    public static final int RESULT_CONNECTION_LIMIT = 3;
    public static final int RESULT_MALFORMED = 4;
    public static final int RESULT_NOT_CONNECTED = 5;
    public static final int RESULT_TPS_LIMIT = 8;
    public static final int RESULT_BINDING_INVALID = 9;
    public static final int RESULT_ACCEPTANCE_REJECTED = 10;

    private final CmppDownstreamCredentialStore credentials;
    private final CmppDownstreamAcceptancePort acceptance;
    private final CmppDownstreamConnectionRegistry connections;
    private final CmppDownstreamSessionRegistry sessions;
    private final Clock clock;
    private final Map<Integer, CmppDownstreamSessionRegistry.PendingDelivery> inflightDeliveries = new LinkedHashMap<>();
    private final AtomicInteger nextSequence = new AtomicInteger(9000);

    private CmppDownstreamCredentialStore.Credential credential;
    private String clientIp;
    private Long lastTenantId;
    private boolean connected;
    private long tpsSecond = -1;
    private int tpsCount;

    public CmppDownstreamGatewaySession(CmppDownstreamCredentialStore credentials,
                                        CmppDownstreamAcceptancePort acceptance,
                                        CmppDownstreamConnectionRegistry connections,
                                        Clock clock) {
        this(credentials, acceptance, connections, new CmppDownstreamSessionRegistry(), clock);
    }

    public CmppDownstreamGatewaySession(CmppDownstreamCredentialStore credentials,
                                        CmppDownstreamAcceptancePort acceptance,
                                        CmppDownstreamConnectionRegistry connections,
                                        CmppDownstreamSessionRegistry sessions,
                                        Clock clock) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.acceptance = Objects.requireNonNull(acceptance, "acceptance");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized CmppPdu handle(CmppPdu request, String remoteIp) {
        try {
            return switch (request.commandId()) {
                case CmppCommands.CONNECT -> connect(request, remoteIp);
                case CmppCommands.ACTIVE_TEST -> activeTest(request);
                case CmppCommands.SUBMIT -> submit(request);
                case CmppCommands.DELIVER_RESP -> deliverResp(request);
                case CmppCommands.TERMINATE -> terminate(request);
                default -> new CmppPdu(request.commandId(), request.sequenceId(), new byte[0]);
            };
        } catch (IllegalArgumentException malformed) {
            return preciseMalformedResponse(request);
        }
    }

    public synchronized void queueReport(long tenantId, String providerMessageId, String messageId, String providerStatus) {
        sessions.queueReport(new CmppDownstreamSessionRegistry.PendingDelivery(
                tenantId, providerMessageId, messageId, providerStatus));
    }

    public synchronized List<CmppPdu> drainDeliveries() {
        if (!connected || credential == null) {
            return List.of();
        }
        List<CmppPdu> deliverable = new java.util.ArrayList<>();
        int capacity = credential.windowSize() - inflightDeliveries.size();
        for (CmppDownstreamSessionRegistry.PendingDelivery next : sessions.claim(credential.tenantId(), capacity)) {
            int sequence = nextSequence();
            inflightDeliveries.put(sequence, next);
            deliverable.add(new CmppPdu(CmppCommands.DELIVER, sequence,
                    CmppBodyCodec.deliverReport(next.providerMessageId(), next.messageId(), next.providerStatus())));
        }
        return deliverable;
    }

    public synchronized void revokeCredential(long credentialId) {
        if (credential != null && credential.credentialId() == credentialId) {
            disconnect();
        }
    }

    public synchronized boolean connected() {
        return connected;
    }

    public synchronized int pendingDeliveryCount() {
        if (credential != null) {
            return sessions.pendingCount(credential.tenantId());
        }
        return lastTenantId == null ? 0 : sessions.pendingCount(lastTenantId);
    }

    public synchronized int inflightDeliveryCount() {
        return inflightDeliveries.size();
    }

    private CmppPdu connect(CmppPdu request, String remoteIp) {
        CmppBodyCodec.Connect body = CmppBodyCodec.connect(request.body());
        var found = credentials.findActiveByAccount(body.sourceAddress());
        if (found.isEmpty()) {
            return connectResp(request, RESULT_AUTH_FAILED, body.version());
        }
        CmppDownstreamCredentialStore.Credential candidate = found.get();
        if (!ipAllowed(candidate.ipWhitelist(), remoteIp)) {
            return connectResp(request, RESULT_IP_DENIED, body.version());
        }
        byte[] expected = CmppAuthenticator.authenticatorSource(candidate.account(), candidate.password(), body.timestamp());
        boolean authOk = MessageDigest.isEqual(expected, body.authenticator());
        Arrays.fill(expected, (byte) 0);
        if (!authOk) {
            return connectResp(request, RESULT_AUTH_FAILED, body.version());
        }
        if (!connections.tryOpen(candidate.credentialId(), candidate.maxConnections())) {
            return connectResp(request, RESULT_CONNECTION_LIMIT, body.version());
        }
        credential = candidate;
        clientIp = remoteIp;
        lastTenantId = candidate.tenantId();
        connected = true;
        sessions.register(candidate.tenantId(), this);
        return connectResp(request, RESULT_OK, body.version());
    }

    private CmppPdu activeTest(CmppPdu request) {
        if (!connected) {
            return new CmppPdu(CmppCommands.ACTIVE_TEST_RESP, request.sequenceId(), new byte[] {(byte) RESULT_NOT_CONNECTED});
        }
        return new CmppPdu(CmppCommands.ACTIVE_TEST_RESP, request.sequenceId(), new byte[0]);
    }

    private CmppPdu submit(CmppPdu request) {
        if (!connected || credential == null) {
            return submitResp(request, "", RESULT_NOT_CONNECTED);
        }
        if (!allowTps()) {
            return submitResp(request, "", RESULT_TPS_LIMIT);
        }
        CmppDownstreamBodyCodec.Submit body = CmppDownstreamBodyCodec.submit(request.body());
        if (!credential.spid().equals(body.serviceId())) {
            return submitResp(request, "", RESULT_BINDING_INVALID);
        }
        var result = acceptance.accept(new CmppDownstreamAcceptancePort.SubmitCommand(
                credential.tenantId(), credential.credentialId(), clientIp, body.submitId(), body.destination(),
                body.serviceId(), body.templateId(), body.productCode(), body.content(), body.registeredDelivery()));
        if (!result.accepted()) {
            return submitResp(request, "", RESULT_ACCEPTANCE_REJECTED);
        }
        return submitResp(request, result.messageId(), RESULT_OK);
    }

    private CmppPdu deliverResp(CmppPdu request) {
        if (!connected) {
            return new CmppPdu(CmppCommands.DELIVER_RESP, request.sequenceId(), new byte[] {(byte) RESULT_NOT_CONNECTED});
        }
        inflightDeliveries.remove(request.sequenceId());
        return new CmppPdu(CmppCommands.DELIVER_RESP, request.sequenceId(), new byte[] {(byte) RESULT_OK});
    }

    private CmppPdu terminate(CmppPdu request) {
        disconnect();
        return new CmppPdu(CmppCommands.TERMINATE_RESP, request.sequenceId(), new byte[0]);
    }

    private CmppPdu preciseMalformedResponse(CmppPdu request) {
        return switch (request.commandId()) {
            case CmppCommands.CONNECT -> connectResp(request, RESULT_MALFORMED, (byte) 0x30);
            case CmppCommands.SUBMIT -> submitResp(request, "", RESULT_MALFORMED);
            default -> new CmppPdu(request.commandId(), request.sequenceId(), new byte[] {(byte) RESULT_MALFORMED});
        };
    }

    private void disconnect() {
        if (credential != null) {
            connections.close(credential.credentialId());
            sessions.unregister(credential.tenantId(), this);
            sessions.returnToPending(credential.tenantId(), inflightDeliveries.values());
        }
        inflightDeliveries.clear();
        connected = false;
        credential = null;
        clientIp = null;
    }

    private boolean allowTps() {
        long now = clock.instant().getEpochSecond();
        if (now != tpsSecond) {
            tpsSecond = now;
            tpsCount = 0;
        }
        tpsCount++;
        return tpsCount <= credential.tpsLimit();
    }

    private int nextSequence() {
        return nextSequence.getAndUpdate(value -> value == Integer.MAX_VALUE ? 9000 : value + 1);
    }

    private static CmppPdu connectResp(CmppPdu request, int status, byte version) {
        return new CmppPdu(CmppCommands.CONNECT_RESP, request.sequenceId(), CmppBodyCodec.connectResp(status, version));
    }

    private static CmppPdu submitResp(CmppPdu request, String providerMessageId, int result) {
        return new CmppPdu(CmppCommands.SUBMIT_RESP, request.sequenceId(),
                CmppBodyCodec.submitResp(providerMessageId, result));
    }

    private static boolean ipAllowed(String whitelist, String remoteIp) {
        if (remoteIp == null || remoteIp.isBlank()) {
            return false;
        }
        for (String token : whitelist.split(",")) {
            String rule = token.trim();
            if ("*".equals(rule) || remoteIp.equals(rule)) {
                return true;
            }
        }
        return false;
    }
}
