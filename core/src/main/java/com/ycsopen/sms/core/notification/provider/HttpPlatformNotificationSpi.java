package com.ycsopen.sms.core.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Narrow configured HTTP transport for platform bootstrap messages. */
@Component
@ConditionalOnProperty(prefix = "ycsopen.platform-notification", name = "enabled", havingValue = "true")
public final class HttpPlatformNotificationSpi implements PlatformNotificationSpi {
    private final RestClient client;
    private final String path;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpPlatformNotificationSpi(
            @Value("${ycsopen.platform-notification.base-url:}") String baseUrl,
            @Value("${ycsopen.platform-notification.path:/messages}") String path,
            @Value("${ycsopen.platform-notification.timeout-ms:800}") int timeoutMs) {
        if (baseUrl == null || baseUrl.isBlank() || path == null || path.isBlank() || timeoutMs < 1) {
            throw new IllegalArgumentException("platform notification transport configuration is invalid");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeoutMs);
        requests.setReadTimeout(timeoutMs);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
        this.path = path;
    }

    @Override
    public DeliveryOutcome send(NotificationRequest request) {
        try {
            String response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("recipient", request.recipient(), "template", request.template().code(),
                            "content", request.content(), "requestId", request.requestId()))
                    .retrieve().body(String.class);
            JsonNode parsed = mapper.readTree(response == null ? "{}" : response);
            String messageId = parsed.path("messageId").asText(null);
            return messageId == null || messageId.isBlank()
                    ? DeliveryOutcome.failed("INVALID_PROVIDER_RESPONSE")
                    : DeliveryOutcome.accepted(messageId);
        } catch (RuntimeException | java.io.IOException failure) {
            return DeliveryOutcome.failed("PROVIDER_UNAVAILABLE");
        }
    }
}
