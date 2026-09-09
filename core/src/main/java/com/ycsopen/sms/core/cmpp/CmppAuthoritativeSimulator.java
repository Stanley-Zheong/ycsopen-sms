package com.ycsopen.sms.core.cmpp;

import com.ycsopen.sms.core.common.exception.BusinessException;

import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/** In-process authoritative simulator that speaks the same PDU contract as the client. */
public final class CmppAuthoritativeSimulator implements CmppGateway {
    private final String sourceAddress;
    private final String password;
    private final Map<String, String> accepted = new LinkedHashMap<>();
    private final Map<String, Integer> rejected = new LinkedHashMap<>();
    private final Map<String, Integer> submitCounts = new LinkedHashMap<>();
    private final Queue<CmppPdu> deliveries = new ArrayDeque<>();
    private boolean connected;
    private boolean holdSubmitResponses;

    public CmppAuthoritativeSimulator(String sourceAddress, String password) {
        this.sourceAddress = sourceAddress;
        this.password = password;
    }

    public void holdSubmitResponses(boolean holdSubmitResponses) {
        this.holdSubmitResponses = holdSubmitResponses;
    }

    public void rejectSubmit(String idempotencyKey, int resultCode) {
        rejected.put(idempotencyKey, resultCode);
    }

    @Override
    public CmppPdu exchange(CmppPdu request) {
        return switch (request.commandId()) {
            case CmppCommands.CONNECT -> connect(request);
            case CmppCommands.ACTIVE_TEST -> connected
                    ? new CmppPdu(CmppCommands.ACTIVE_TEST_RESP, request.sequenceId(), new byte[0])
                    : throwNotConnected();
            case CmppCommands.TERMINATE -> {
                connected = false;
                yield new CmppPdu(CmppCommands.TERMINATE_RESP, request.sequenceId(), new byte[0]);
            }
            case CmppCommands.SUBMIT -> submit(request);
            case CmppCommands.DELIVER_RESP -> new CmppPdu(CmppCommands.DELIVER_RESP, request.sequenceId(), new byte[0]);
            default -> throw new BusinessException("CMPP_COMMAND_UNSUPPORTED", "模拟器不支持该 CMPP 命令");
        };
    }

    @Override
    public List<CmppPdu> drainDeliveries() {
        List<CmppPdu> drained = deliveries.stream().toList();
        deliveries.clear();
        return drained;
    }

    public void enqueueReceipt(String providerMessageId, String messageId, String providerStatus) {
        deliveries.add(new CmppPdu(CmppCommands.DELIVER, 7000 + deliveries.size(),
                CmppBodyCodec.deliverReport(providerMessageId, messageId, providerStatus)));
    }

    public void enqueueUplink(String sourceAddress, String content) {
        deliveries.add(new CmppPdu(CmppCommands.DELIVER, 8000 + deliveries.size(),
                CmppBodyCodec.uplink(sourceAddress, content)));
    }

    public int acceptedLogicalMessages() {
        return accepted.size();
    }

    public int submitCount(String idempotencyKey) {
        return submitCounts.getOrDefault(idempotencyKey, 0);
    }

    private CmppPdu connect(CmppPdu request) {
        CmppBodyCodec.Connect body = CmppBodyCodec.connect(request.body());
        byte[] expected = CmppAuthenticator.authenticatorSource(sourceAddress, password, body.timestamp());
        boolean ok = sourceAddress.equals(body.sourceAddress())
                && MessageDigest.isEqual(expected, body.authenticator());
        Arrays.fill(expected, (byte) 0);
        connected = ok;
        return new CmppPdu(CmppCommands.CONNECT_RESP, request.sequenceId(),
                CmppBodyCodec.connectResp(ok ? 0 : 1, body.version()));
    }

    private CmppPdu submit(CmppPdu request) {
        if (!connected) {
            return throwNotConnected();
        }
        CmppBodyCodec.Submit body = CmppBodyCodec.submit(request.body());
        submitCounts.merge(body.idempotencyKey(), 1, Integer::sum);
        Integer rejectedResult = rejected.get(body.idempotencyKey());
        if (rejectedResult != null) {
            return new CmppPdu(CmppCommands.SUBMIT_RESP, request.sequenceId(),
                    CmppBodyCodec.submitResp("", rejectedResult));
        }
        String providerMessageId = accepted.computeIfAbsent(body.idempotencyKey(),
                key -> "CMPP-" + Math.abs(key.hashCode()));
        if (holdSubmitResponses) {
            return null;
        }
        return new CmppPdu(CmppCommands.SUBMIT_RESP, request.sequenceId(),
                CmppBodyCodec.submitResp(providerMessageId, 0));
    }

    private CmppPdu throwNotConnected() {
        throw new BusinessException("CMPP_NOT_CONNECTED", "模拟器连接未建立");
    }
}
