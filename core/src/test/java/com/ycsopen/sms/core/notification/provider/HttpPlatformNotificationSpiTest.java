package com.ycsopen.sms.core.notification.provider;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPlatformNotificationSpiTest {
    @Test
    void sendsTheConfiguredProviderPayloadAndReturnsOnlyTheSafeProviderMessageId() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{\"messageId\":\"sandbox-42\"}".getBytes();
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpPlatformNotificationSpi spi = new HttpPlatformNotificationSpi(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "/messages", 500);

            PlatformNotificationSpi.DeliveryOutcome result = spi.send(
                    new PlatformNotificationSpi.NotificationRequest("13800138000",
                            PlatformNotificationTemplate.REGISTRATION, "验证码 123456", "request-42"));

            assertThat(result.status()).isEqualTo(PlatformNotificationSpi.DeliveryOutcome.Status.ACCEPTED);
            assertThat(result.providerMessageId()).isEqualTo("sandbox-42");
            assertThat(requestBody.get()).contains("13800138000", "platform.registration", "request-42");
        } finally {
            server.stop(0);
        }
    }
}
