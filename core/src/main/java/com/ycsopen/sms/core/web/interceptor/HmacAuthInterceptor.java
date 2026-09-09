package com.ycsopen.sms.core.web.interceptor;

import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import com.ycsopen.sms.core.domain.entity.TenantApiKey.Status;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository.AuthenticationProjection;
import com.ycsopen.sms.core.service.routing.ApiKeyRateLimitService.RatePolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** F-6.4 SMS API authentication compatibility interceptor. The complete body/IP/secret
 * check runs in {@code HmacSmsAuthenticationFilter}; this interceptor only preserves the
 * legacy header-only test boundary and passes through requests already authenticated by the filter. */
@Component
public class HmacAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_TENANT_ID = "ycsopen.tenantId";
    public static final String ATTR_API_KEY_ID = "ycsopen.apiKeyId";
    public static final String ATTR_RATE_POLICY = "ycsopen.ratePolicy";

    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final HmacSignatureVerifier hmacSignatureVerifier;

    public HmacAuthInterceptor(TenantApiKeyRepository tenantApiKeyRepository,
                                HmacSignatureVerifier hmacSignatureVerifier) {
        this.tenantApiKeyRepository = tenantApiKeyRepository;
        this.hmacSignatureVerifier = hmacSignatureVerifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getAttribute(ATTR_TENANT_ID) != null && request.getAttribute(ATTR_API_KEY_ID) != null) {
            return true;
        }
        String appKey = request.getHeader("X-App-Key");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String signature = request.getHeader("X-Signature");

        if (appKey == null || timestamp == null || nonce == null || signature == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "缺少必需的鉴权请求头");
            return false;
        }

        AuthenticationProjection apiKey = tenantApiKeyRepository.findAuthenticationByAppKey(appKey).orElse(null);
        if (apiKey == null || apiKey.getStatus() != Status.ACTIVE) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "无效的 App Key");
            return false;
        }

        if (!hmacSignatureVerifier.verifyTimestamp(Long.parseLong(timestamp))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "时间戳超出 5 分钟有效期");
            return false;
        }
        if (!hmacSignatureVerifier.checkAndRecordNonce(nonce)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "nonce 重复，疑似重放攻击");
            return false;
        }

        request.setAttribute(ATTR_TENANT_ID, apiKey.getTenantId());
        request.setAttribute(ATTR_API_KEY_ID, apiKey.getId());
        request.setAttribute(ATTR_RATE_POLICY, new RatePolicy(apiKey.getRateLimitPerSec(),
                apiKey.getRateLimitPerMin(), apiKey.getRateLimitPerHour(), apiKey.getRateLimitPerDay()));
        return true;
    }
}
