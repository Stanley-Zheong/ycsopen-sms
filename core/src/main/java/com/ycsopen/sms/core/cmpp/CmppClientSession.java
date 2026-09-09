package com.ycsopen.sms.core.cmpp;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Stateful CMPP upstream client core: auth, sequence/window, heartbeat, submit, reports, and reconnect. */
public final class CmppClientSession {
    private final Config config;
    private final ProviderStatusTaxonomyPort taxonomy;
    private final CmppSubmitSegmenter segmenter = new CmppSubmitSegmenter();
    private final AtomicInteger nextSequence = new AtomicInteger(1);
    private final Map<Integer, PendingSegment> inflight = new LinkedHashMap<>();
    private final Map<String, Map<Integer, String>> partialProviderIds = new LinkedHashMap<>();
    private final Map<String, SubmitResult> completed = new LinkedHashMap<>();
    private boolean connected;

    public CmppClientSession(Config config, ProviderStatusTaxonomyPort taxonomy) {
        this.config = Objects.requireNonNull(config, "config").checked();
        this.taxonomy = Objects.requireNonNull(taxonomy, "taxonomy");
    }

    public void connect(CmppGateway gateway, int timestamp) {
        byte[] auth = CmppAuthenticator.authenticatorSource(config.sourceAddress(), config.password(), timestamp);
        CmppPdu response = gateway.exchange(new CmppPdu(CmppCommands.CONNECT, nextSequence(),
                CmppBodyCodec.connect(config.sourceAddress(), auth, config.version(), timestamp)));
        requireCommand(response, CmppCommands.CONNECT_RESP);
        CmppBodyCodec.ConnectResp body = CmppBodyCodec.connectResp(response.body());
        if (body.status() != 0) {
            throw new BusinessException("CMPP_CONNECT_REJECTED", "上游 CMPP 鉴权失败");
        }
        connected = true;
    }

    public boolean activeTest(CmppGateway gateway) {
        requireConnected();
        CmppPdu response = gateway.exchange(new CmppPdu(CmppCommands.ACTIVE_TEST, nextSequence(), new byte[0]));
        requireCommand(response, CmppCommands.ACTIVE_TEST_RESP);
        return true;
    }

    public void terminate(CmppGateway gateway) {
        if (!connected) {
            return;
        }
        CmppPdu response = gateway.exchange(new CmppPdu(CmppCommands.TERMINATE, nextSequence(), new byte[0]));
        requireCommand(response, CmppCommands.TERMINATE_RESP);
        connected = false;
        inflight.clear();
    }

    public SubmitResult submit(CmppGateway gateway, SubmitCommand command) {
        SubmitCommand checked = command.checked();
        SubmitResult existing = completed.get(checked.idempotencyKey());
        if (existing != null) {
            return existing;
        }
        if (hasInflight(checked.idempotencyKey())) {
            throw new BusinessException("CMPP_SUBMIT_UNKNOWN", "CMPP 提交结果未知，保留任务声明等待重连确认");
        }
        requireConnected();
        List<CmppSubmitSegmenter.Segment> segments = segmenter.segment(checked.content());
        Map<Integer, String> acceptedSegments = partialProviderIds.computeIfAbsent(
                checked.idempotencyKey(), key -> new LinkedHashMap<>());
        List<String> providerIds = new ArrayList<>();
        for (CmppSubmitSegmenter.Segment segment : segments) {
            String acceptedProviderId = acceptedSegments.get(segment.index());
            if (acceptedProviderId != null) {
                providerIds.add(acceptedProviderId);
                continue;
            }
            if (inflight.size() >= config.windowSize()) {
                throw new BusinessException("CMPP_WINDOW_BACKPRESSURE", "CMPP 窗口已满");
            }
            int sequence = nextSequence();
            inflight.put(sequence, new PendingSegment(checked.idempotencyKey(), checked.messageId(), segment.index()));
            CmppPdu response = gateway.exchange(new CmppPdu(CmppCommands.SUBMIT, sequence,
                    CmppBodyCodec.submit(checked.idempotencyKey(), checked.messageId(), checked.destination(),
                            segment.reference(), segment.total(), segment.index(),
                            checked.registeredDelivery(), segment.payload())));
            if (response == null) {
                continue;
            }
            SubmitResult segmentResult = submitResponse(response);
            if (!"ACCEPTED".equals(segmentResult.status())) {
                partialProviderIds.remove(checked.idempotencyKey());
                completed.put(checked.idempotencyKey(), segmentResult);
                return segmentResult;
            }
            acceptedSegments.put(segment.index(), segmentResult.providerMessageId());
            providerIds.add(segmentResult.providerMessageId());
        }
        if (hasInflight(checked.idempotencyKey())) {
            throw new BusinessException("CMPP_SUBMIT_UNKNOWN", "CMPP 提交结果未知，保留任务声明等待重连确认");
        }
        if (acceptedSegments.size() != segments.size()) {
            throw new BusinessException("CMPP_SUBMIT_UNKNOWN", "CMPP 提交结果未知，保留任务声明等待重连确认");
        }
        SubmitResult result = SubmitResult.accepted(checked.messageId(), String.join(",", providerIds));
        completed.put(checked.idempotencyKey(), result);
        partialProviderIds.remove(checked.idempotencyKey());
        return result;
    }

    public SubmitResult submitResponse(CmppPdu response) {
        requireCommand(response, CmppCommands.SUBMIT_RESP);
        PendingSegment pending = inflight.remove(response.sequenceId());
        if (pending == null) {
            throw new BusinessException("CMPP_SEQUENCE_UNKNOWN", "CMPP 响应序列未知");
        }
        CmppBodyCodec.SubmitResp body = CmppBodyCodec.submitResp(response.body());
        if (body.result() != 0) {
            SubmitResult rejected = SubmitResult.rejected(pending.messageId(), "CMPP_" + body.result());
            completed.putIfAbsent(pending.idempotencyKey(), rejected);
            return rejected;
        }
        return SubmitResult.accepted(pending.messageId(), body.providerMessageId());
    }

    public ReconnectPlan disconnect() {
        connected = false;
        return new ReconnectPlan(inflight.size(), config.reconnect().delays());
    }

    public List<NormalizedEvent> drainDeliveries(CmppGateway gateway) {
        List<NormalizedEvent> events = new ArrayList<>();
        for (CmppPdu pdu : gateway.drainDeliveries()) {
            requireCommand(pdu, CmppCommands.DELIVER);
            CmppBodyCodec.Deliver body = CmppBodyCodec.decodeDeliver(pdu.body());
            if (body.report()) {
                ProviderStatusTaxonomyPort.NormalizedStatus status =
                        taxonomy.normalize(config.providerName(), "CMPP", body.second());
                events.add(new NormalizedEvent("RECEIPT", body.first(), null, body.second(),
                        status.platformCategory(), status.finalState(), status.billable(), status.retryable()));
            } else {
                events.add(new NormalizedEvent("UPLINK", null, body.first(), body.second(),
                        "UPLINK", false, false, false));
            }
            gateway.exchange(new CmppPdu(CmppCommands.DELIVER_RESP, pdu.sequenceId(),
                    CmppBodyCodec.deliverResp(body.first(), 0)));
        }
        return events;
    }

    public int nextSequenceForTest() {
        return nextSequence();
    }

    public int inflight() {
        return inflight.size();
    }

    private boolean hasInflight(String idempotencyKey) {
        return inflight.values().stream().anyMatch(pending -> pending.idempotencyKey().equals(idempotencyKey));
    }

    private int nextSequence() {
        return nextSequence.getAndUpdate(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
    }

    private void requireConnected() {
        if (!connected) {
            throw new BusinessException("CMPP_NOT_CONNECTED", "CMPP 连接未建立");
        }
    }

    private static void requireCommand(CmppPdu pdu, int expected) {
        if (pdu == null || pdu.commandId() != expected) {
            throw new BusinessException("CMPP_COMMAND_UNEXPECTED", "CMPP 响应命令不匹配");
        }
    }

    private record PendingSegment(String idempotencyKey, String messageId, int segmentIndex) { }

    public record Config(String providerName, String sourceAddress, String password, byte version,
                         int windowSize, ReconnectPolicy reconnect) {
        Config checked() {
            if (blank(providerName) || blank(sourceAddress) || blank(password) || windowSize <= 0) {
                throw new IllegalArgumentException("invalid CMPP client config");
            }
            return new Config(providerName.trim(), sourceAddress.trim(), password, version, windowSize,
                    reconnect == null ? ReconnectPolicy.standard() : reconnect);
        }
    }

    public record ReconnectPolicy(List<Duration> delays) {
        public static ReconnectPolicy standard() {
            return new ReconnectPolicy(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)));
        }

        public ReconnectPolicy {
            if (delays == null || delays.isEmpty()) {
                throw new IllegalArgumentException("reconnect delays are required");
            }
            delays = List.copyOf(delays);
        }
    }

    public record ReconnectPlan(int retainedClaims, List<Duration> backoffDelays) { }

    public record SubmitCommand(String idempotencyKey, String messageId, String destination,
                                String content, boolean registeredDelivery) {
        SubmitCommand checked() {
            if (blank(idempotencyKey) || blank(messageId) || blank(destination) || blank(content)) {
                throw new IllegalArgumentException("invalid CMPP submit command");
            }
            if (!destination.matches("1[3-9][0-9]{9}")) {
                throw new IllegalArgumentException("invalid CMPP destination");
            }
            return new SubmitCommand(idempotencyKey.trim(), messageId.trim(), destination.trim(),
                    content.trim(), registeredDelivery);
        }
    }

    public record SubmitResult(String messageId, String status, String providerMessageId, String errorCode) {
        static SubmitResult accepted(String messageId, String providerMessageId) {
            return new SubmitResult(messageId, "ACCEPTED", providerMessageId, null);
        }

        static SubmitResult rejected(String messageId, String errorCode) {
            return new SubmitResult(messageId, "REJECTED", null, errorCode);
        }
    }

    public record NormalizedEvent(String kind, String providerMessageId, String sourceAddress,
                                  String providerStatusOrContent, String platformCategory,
                                  boolean finalState, boolean billable, boolean retryable) { }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
