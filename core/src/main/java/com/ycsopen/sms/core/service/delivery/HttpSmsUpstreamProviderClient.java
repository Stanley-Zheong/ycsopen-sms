package com.ycsopen.sms.core.service.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Configured HTTP upstream connector. Payload is intentionally narrow and idempotency-keyed. */
@Component
@ConditionalOnProperty(prefix = "ycsopen.sms.upstream-http", name = "enabled", havingValue = "true")
public class HttpSmsUpstreamProviderClient implements SmsUpstreamProviderClient {
    private final RestClient client;
    private final String path;
    private final ObjectMapper json;

    public HttpSmsUpstreamProviderClient(
            @Value("${ycsopen.sms.upstream-http.base-url:}") String baseUrl,
            @Value("${ycsopen.sms.upstream-http.path:/messages}") String path,
            @Value("${ycsopen.sms.upstream-http.timeout-ms:1000}") int timeoutMs,
            ObjectMapper json) {
        if (baseUrl == null || baseUrl.isBlank() || path == null || path.isBlank() || timeoutMs < 1) {
            throw new IllegalArgumentException("sms upstream HTTP configuration is invalid");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeoutMs);
        requests.setReadTimeout(timeoutMs);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
        this.path = path;
        this.json = json;
    }

    @Override
    public ProviderSubmitResult submit(ProviderSubmitRequest request) {
        try {
            String response = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", request.idempotencyKey())
                    .body(Map.of(
                            "messageId", request.messageId(),
                            "tenantId", request.tenantId(),
                            "taskId", request.taskId(),
                            "channelId", request.channelId(),
                            "recipient", request.recipient(),
                            "content", request.content()))
                    .retrieve()
                    .body(String.class);
            JsonNode root = json.readTree(response == null ? "{}" : response);
            String providerMessageId = text(root, "providerMessageId");
            String status = text(root, "status");
            if ("REJECTED".equalsIgnoreCase(status)) {
                return ProviderSubmitResult.rejected(text(root, "errorCode"), text(root, "errorMessage"));
            }
            if (providerMessageId == null) {
                return ProviderSubmitResult.unknown("INVALID_PROVIDER_RESPONSE", "provider response did not include providerMessageId");
            }
            return ProviderSubmitResult.accepted(providerMessageId);
        } catch (RuntimeException | java.io.IOException failure) {
            return ProviderSubmitResult.unknown("PROVIDER_UNAVAILABLE", "provider response was not confirmed");
        }
    }

    private static String text(JsonNode root, String field) {
        if (root == null || root.path(field).isMissingNode() || root.path(field).isNull()
                || root.path(field).asText().isBlank()) {
            return null;
        }
        return root.path(field).asText();
    }
}
