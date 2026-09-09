package com.ycsopen.sms.core.service.webhook;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;

@Component
public class HttpWebhookDeliveryClient implements WebhookDeliveryClient {
    private final RestTemplate restTemplate;

    public HttpWebhookDeliveryClient(@Value("${ycsopen.webhook-delivery.timeout-ms:1500}") int timeoutMs) {
        if (timeoutMs < 1) {
            throw new IllegalArgumentException("webhook delivery timeout must be positive");
        }
        SimpleClientHttpRequestFactory requests = new NoRedirectRequestFactory();
        requests.setConnectTimeout(timeoutMs);
        requests.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(requests);
    }

    @Override
    public DeliveryResponse post(String destinationUrl, String payloadJson, Map<String, String> headers) {
        WebhookDeliveryTransportService.validateDestination("HTTP", destinationUrl);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        try {
            var response = restTemplate.postForEntity(destinationUrl, new HttpEntity<>(payloadJson, httpHeaders), String.class);
            return new DeliveryResponse(response.getStatusCode().value(), "HTTP_" + response.getStatusCode().value(),
                    response.getBody() == null ? null : response.getBody());
        } catch (RestClientException ex) {
            return new DeliveryResponse(0, "NETWORK_ERROR", ex.getClass().getSimpleName());
        }
    }

    private static final class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
