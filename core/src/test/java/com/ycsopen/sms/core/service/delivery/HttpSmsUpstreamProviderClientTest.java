package com.ycsopen.sms.core.service.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpSmsUpstreamProviderClientTest {
    @Test
    void postsIdempotentHttpPayloadToSandboxProvider() throws Exception {
        AtomicReference<String> idempotency = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/messages", exchange -> {
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] response = "{\"providerMessageId\":\"UP-HTTP-1\",\"status\":\"ACCEPTED\"}".getBytes();
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpSmsUpstreamProviderClient client = new HttpSmsUpstreamProviderClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "/messages", 500, new ObjectMapper());

            var result = client.submit(new SmsUpstreamProviderClient.ProviderSubmitRequest(
                    17L, 91L, "MSG_1700000000000_ABCDEF12", 42L,
                    "13800138000", "验证码 123456", "claim-token-1"));

            assertThat(result.status()).isEqualTo(SmsUpstreamProviderClient.ProviderSubmitResult.Status.ACCEPTED);
            assertThat(result.providerMessageId()).isEqualTo("UP-HTTP-1");
            assertThat(idempotency.get()).isEqualTo("claim-token-1");
            assertThat(body.get()).contains("13800138000", "MSG_1700000000000_ABCDEF12", "验证码 123456");
        } finally {
            server.stop(0);
        }
    }
}
