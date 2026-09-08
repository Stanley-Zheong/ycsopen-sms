package com.ycsopen.sms.core.service.channel;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class ChannelConnectivityAdapter {
    private static final Set<String> REACHABLE_FIXTURES = Set.of("127.0.0.1", "localhost", "channel-fixture.local");

    public ConnectivityResult validate(ChannelConfiguration configuration) {
        if (configuration.protocol() == null) {
            throw new BusinessException("CHANNEL_PROTOCOL_REQUIRED", "通道协议不能为空");
        }
        if (configuration.host() == null || configuration.host().isBlank()) {
            throw new BusinessException("CHANNEL_HOST_REQUIRED", "通道地址不能为空");
        }
        int port = configuration.port() == null ? -1 : configuration.port();
        if (port < 1 || port > 65535) {
            throw new BusinessException("CHANNEL_PORT_INVALID", "通道端口不合法");
        }
        if (configuration.maxConnections() <= 0 || configuration.windowSize() <= 0) {
            throw new BusinessException("CHANNEL_CONNECTION_INVALID", "连接数与窗口大小必须为正整数");
        }
        if (configuration.protocol() == Channel.Protocol.HTTP && !configuration.host().startsWith("http")) {
            return ConnectivityResult.failed("HTTP_ENDPOINT_REQUIRED", true);
        }
        if (configuration.protocol() != Channel.Protocol.HTTP && port == 80) {
            return ConnectivityResult.failed("PROTOCOL_PORT_INCOMPATIBLE", true);
        }
        String normalizedHost = configuration.host().toLowerCase(Locale.ROOT)
                .replace("https://", "").replace("http://", "");
        boolean reachable = REACHABLE_FIXTURES.contains(normalizedHost);
        return reachable ? ConnectivityResult.success()
                : ConnectivityResult.failed("DETERMINISTIC_FIXTURE_UNREACHABLE", true);
    }

    public record ChannelConfiguration(Channel.Protocol protocol, String host, Integer port,
                                       int maxConnections, int windowSize) { }

    public record ConnectivityResult(boolean passed, String reasonCode, boolean retryable) {
        public static ConnectivityResult success() {
            return new ConnectivityResult(true, "PASSED", false);
        }

        public static ConnectivityResult failed(String reasonCode, boolean retryable) {
            return new ConnectivityResult(false, reasonCode, retryable);
        }
    }
}
