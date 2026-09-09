package com.ycsopen.sms.core.service.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Configured HTTP adapter for a bounded external inspection service. */
@Component
public final class HttpQualificationInspectionSpi implements QualificationInspectionSpi {
    private static final int MAXIMUM_RESPONSE_BYTES = 16_384;
    private final URI endpoint;
    private final String credential;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper json;

    @Autowired
    public HttpQualificationInspectionSpi(
            @Value("${ycsopen.tenant-qualification.inspection.endpoint:}") String endpoint,
            @Value("${ycsopen.tenant-qualification.inspection.credential:}") String credential,
            @Value("${ycsopen.tenant-qualification.inspection.timeout:5s}") Duration timeout) {
        this(endpoint, credential, timeout, HttpClient.newBuilder().connectTimeout(timeout).build(),
                new ObjectMapper());
    }

    HttpQualificationInspectionSpi(String endpoint, String credential, Duration timeout,
                                   HttpClient client, ObjectMapper json) {
        if (endpoint == null || endpoint.isBlank()) {
            this.endpoint = null;
            this.credential = credential;
            this.timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
            this.client = client;
            this.json = json;
            return;
        }
        try {
            this.endpoint = URI.create(endpoint);
            if (!"http".equals(this.endpoint.getScheme()) && !"https".equals(this.endpoint.getScheme())) {
                throw new IllegalArgumentException();
            }
            String host = this.endpoint.getHost();
            if ("http".equals(this.endpoint.getScheme())
                    && !("127.0.0.1".equals(host) || "::1".equals(host) || "localhost".equalsIgnoreCase(host))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) {
            throw QualificationInspectionSpi.Failure.unavailable();
        }
        if (credential == null || credential.isBlank() || timeout == null || timeout.isNegative()
                || timeout.isZero()) throw QualificationInspectionSpi.Failure.unavailable();
        this.credential = credential;
        this.timeout = timeout;
        this.client = client;
        this.json = json;
    }

    @Override
    public Facts inspect(Document document) {
        if (endpoint == null || credential == null || credential.isBlank()) {
            document.destroy();
            throw QualificationInspectionSpi.Failure.unavailable();
        }
        byte[] bytes = document.bytes();
        try {
            String body = json.writeValueAsString(new ProviderRequest(document.mediaType(),
                    Base64.getEncoder().encodeToString(bytes)));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + credential)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBody;
            try (InputStream input = response.body()) {
                responseBody = readBounded(input);
            }
            try {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw QualificationInspectionSpi.Failure.unavailable();
                }
                ProviderResponse parsed = json.readValue(responseBody, ProviderResponse.class);
                Facts facts = new Facts(parsed.companyName(), parsed.creditCode(), parsed.confidence(), parsed.requestId());
                validate(facts);
                return facts;
            } finally {
                java.util.Arrays.fill(responseBody, (byte) 0);
            }
        } catch (QualificationInspectionSpi.Failure failure) {
            throw failure;
        } catch (Exception failure) {
            throw QualificationInspectionSpi.Failure.unavailable();
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
            document.destroy();
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(MAXIMUM_RESPONSE_BYTES);
        byte[] buffer = new byte[4_096];
        int total = 0;
        while (true) {
            int permitted = Math.min(buffer.length, MAXIMUM_RESPONSE_BYTES + 1 - total);
            int count = input.read(buffer, 0, permitted);
            if (count < 0) return output.toByteArray();
            total += count;
            if (total > MAXIMUM_RESPONSE_BYTES) {
                throw QualificationInspectionSpi.Failure.unavailable();
            }
            output.write(buffer, 0, count);
        }
    }

    private static void validate(Facts facts) {
        if (facts.companyName() == null || facts.companyName().isBlank() || facts.companyName().length() > 100
                || facts.companyName().chars().anyMatch(Character::isISOControl)
                || facts.creditCode() == null || !facts.creditCode().matches("[0-9A-Z]{18}")
                || !Double.isFinite(facts.confidence()) || facts.confidence() < 0 || facts.confidence() > 1
                || facts.requestId() == null || !facts.requestId().matches("[A-Za-z0-9._:-]{1,100}")) {
            throw QualificationInspectionSpi.Failure.unavailable();
        }
    }

    private record ProviderRequest(String mediaType, String contentBase64) { }
    private record ProviderResponse(String companyName, String creditCode, double confidence, String requestId) { }
}
