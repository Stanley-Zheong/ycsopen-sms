package com.ycsopen.sms.core.service.tenant;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpQualificationInspectionSpiTest {
    @Test
    void callsRealLocalHttpSandboxAndReturnsOnlyBoundedFacts() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inspect", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"companyName\":\"示例机构有限公司\",\"creditCode\":\"91350211M000100Y46\",\"confidence\":0.98,\"requestId\":\"sandbox-1\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpQualificationInspectionSpi adapter = new HttpQualificationInspectionSpi(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/inspect", "sandbox-secret",
                    Duration.ofSeconds(2));
            QualificationInspectionSpi.Facts facts = adapter.inspect(
                    new QualificationInspectionSpi.Document("application/pdf", "license".getBytes(StandardCharsets.UTF_8)));
            assertThat(facts.companyName()).isEqualTo("示例机构有限公司");
            assertThat(facts.creditCode()).isEqualTo("91350211M000100Y46");
            assertThat(request.get()).contains("application/pdf", "bGljZW5zZQ==").doesNotContain("sandbox-secret");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerDiagnosticsAndMalformedFactsCollapseToSafeUnavailableFailure() {
        HttpQualificationInspectionSpi adapter = new HttpQualificationInspectionSpi(
                "http://127.0.0.1:1/inspect", "secret", Duration.ofMillis(100));
        assertThatThrownBy(() -> adapter.inspect(new QualificationInspectionSpi.Document(
                "application/pdf", new byte[]{1})))
                .isExactlyInstanceOf(QualificationInspectionSpi.Failure.class)
                .hasMessage("qualification inspection unavailable");
    }

    @Test
    void oversizedChunkedSuccessAndErrorResponsesAreBoundedAndFailSafely() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] chunk = "x".repeat(4096).getBytes(StandardCharsets.US_ASCII);
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> writes =
                new java.util.concurrent.ConcurrentHashMap<>();
        writes.put("oversized-success", new java.util.concurrent.atomic.AtomicInteger());
        writes.put("oversized-error", new java.util.concurrent.atomic.AtomicInteger());
        server.createContext("/oversized-success", exchange -> oversized(exchange, 200, chunk,
                writes.get("oversized-success")));
        server.createContext("/oversized-error", exchange -> oversized(exchange, 503, chunk,
                writes.get("oversized-error")));
        server.start();
        try {
            for (String path : new String[]{"oversized-success", "oversized-error"}) {
                HttpQualificationInspectionSpi adapter = new HttpQualificationInspectionSpi(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/" + path,
                        "sandbox-secret", Duration.ofSeconds(2));
                assertThatThrownBy(() -> adapter.inspect(new QualificationInspectionSpi.Document(
                        "application/pdf", new byte[]{1})))
                        .isExactlyInstanceOf(QualificationInspectionSpi.Failure.class)
                        .hasMessage("qualification inspection unavailable");
                assertThat(writes.get(path).get()).isLessThan(8192);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void oversized(com.sun.net.httpserver.HttpExchange exchange, int status,
                                   byte[] chunk, java.util.concurrent.atomic.AtomicInteger writes)
            throws java.io.IOException {
        exchange.sendResponseHeaders(status, 0);
        try {
            for (int index = 0; index < 8192; index++) {
                exchange.getResponseBody().write(chunk);
                writes.incrementAndGet();
            }
        } catch (java.io.IOException clientClosed) {
            // Expected when the bounded client closes the chunked response at max+1.
        } finally {
            exchange.close();
        }
    }
}
