package com.ycsopen.sms.core.web.security;

import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import com.ycsopen.sms.core.domain.entity.TenantApiKey;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository.SignatureAuthenticationProjection;
import com.ycsopen.sms.core.service.tenant.TenantCredentialSecretProtectionService;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HmacRequestAuthenticatorTest {
    private final TenantApiKeyRepository apiKeys = mock(TenantApiKeyRepository.class);
    private final TenantCredentialSecretProtectionService secrets = mock(TenantCredentialSecretProtectionService.class);
    private final HmacSignatureVerifier signatures = new HmacSignatureVerifier();
    private final HmacRequestAuthenticator authenticator =
            new HmacRequestAuthenticator(apiKeys, signatures, secrets);

    @Test
    void acceptsOnlySignatureCoveringMethodUriHeadersAndExactBody() {
        byte[] encrypted = "opaque".getBytes(StandardCharsets.UTF_8);
        SignatureAuthenticationProjection projection = activeProjection(encrypted, "127.0.0.1/32");
        when(apiKeys.findSignatureAuthenticationByAppKey("app-key")).thenReturn(Optional.of(projection));
        when(secrets.reveal(17L, 19L, "app_secret_encrypted", encrypted))
                .thenReturn("app-secret".toCharArray());
        String body = "{\"submitId\":\"SUBMIT-1\",\"phoneNumber\":\"13900000001\"}";
        MockHttpServletRequest request = signedRequest(body, "nonce-1", "127.0.0.1", "app-secret");

        authenticator.authenticate(request, body.getBytes(StandardCharsets.UTF_8));

        assertThat(request.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID)).isEqualTo(17L);
        assertThat(request.getAttribute(HmacAuthInterceptor.ATTR_API_KEY_ID)).isEqualTo(19L);
        assertThat(request.getAttribute("ycsopen.requestBodySha256")).isInstanceOf(String.class);

        MockHttpServletRequest tampered = signedRequest(body, "nonce-2", "127.0.0.1", "app-secret");
        assertThatThrownBy(() -> authenticator.authenticate(tampered,
                "{\"submitId\":\"SUBMIT-1\",\"phoneNumber\":\"13900000002\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(HmacRequestAuthenticator.HmacAuthenticationException.class)
                .hasMessage("签名校验失败");
    }

    @Test
    void rejectsDisallowedIpBeforeBusinessAttributesAreSet() {
        byte[] encrypted = "opaque".getBytes(StandardCharsets.UTF_8);
        SignatureAuthenticationProjection projection = activeProjection(encrypted, "10.0.0.0/24");
        when(apiKeys.findSignatureAuthenticationByAppKey("app-key")).thenReturn(Optional.of(projection));
        String body = "{\"submitId\":\"SUBMIT-2\"}";
        MockHttpServletRequest request = signedRequest(body, "nonce-ip", "127.0.0.1", "app-secret");

        assertThatThrownBy(() -> authenticator.authenticate(request, body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(HmacRequestAuthenticator.HmacAuthenticationException.class)
                .hasMessage("来源 IP 不在白名单内");

        assertThat(request.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID)).isNull();
    }

    @Test
    void rejectsReusedNonceBeforeBusinessAttributesAreSet() {
        byte[] encrypted = "opaque".getBytes(StandardCharsets.UTF_8);
        SignatureAuthenticationProjection projection = activeProjection(encrypted, null);
        when(apiKeys.findSignatureAuthenticationByAppKey("app-key")).thenReturn(Optional.of(projection));
        when(secrets.reveal(17L, 19L, "app_secret_encrypted", encrypted))
                .thenReturn("app-secret".toCharArray(), "app-secret".toCharArray());
        String body = "{\"submitId\":\"SUBMIT-3\"}";

        authenticator.authenticate(signedRequest(body, "nonce-replay", "127.0.0.1", "app-secret"),
                body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest replay = signedRequest(body, "nonce-replay", "127.0.0.1", "app-secret");

        assertThatThrownBy(() -> authenticator.authenticate(replay, body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(HmacRequestAuthenticator.HmacAuthenticationException.class)
                .hasMessage("nonce 重复，疑似重放攻击");

        assertThat(replay.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID)).isNull();
    }

    private MockHttpServletRequest signedRequest(String body, String nonce, String ip, String secret) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sms/send");
        request.setRemoteAddr(ip);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        request.addHeader("X-App-Key", "app-key");
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        String stringToSign = signatures.buildStringToSign("POST", "/api/v1/sms/send", "",
                HmacRequestAuthenticator.canonicalHeaders("app-key", timestamp, nonce), body);
        request.addHeader("X-Signature", signatures.sign(stringToSign, secret));
        return request;
    }

    private static SignatureAuthenticationProjection activeProjection(byte[] encrypted, String whitelist) {
        SignatureAuthenticationProjection projection = mock(SignatureAuthenticationProjection.class);
        when(projection.getId()).thenReturn(19L);
        when(projection.getTenantId()).thenReturn(17L);
        when(projection.getStatus()).thenReturn(TenantApiKey.Status.ACTIVE);
        when(projection.getAppSecretEncrypted()).thenReturn(encrypted);
        when(projection.getIpWhitelist()).thenReturn(whitelist);
        when(projection.getRateLimitPerSec()).thenReturn(10);
        when(projection.getRateLimitPerMin()).thenReturn(100);
        when(projection.getRateLimitPerHour()).thenReturn(1000);
        when(projection.getRateLimitPerDay()).thenReturn(10000);
        return projection;
    }
}
