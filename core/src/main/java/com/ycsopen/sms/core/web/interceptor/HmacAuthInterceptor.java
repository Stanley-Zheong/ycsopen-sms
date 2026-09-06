package com.ycsopen.sms.core.web.interceptor;

import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import com.ycsopen.sms.core.domain.entity.TenantApiKey.Status;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository.AuthenticationProjection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * F-6.4 HTTP API 鉴权拦截器：校验 X-App-Key / X-Timestamp / X-Nonce / X-Signature 四件套。
 * <p>当前实现读取 header 后交给 {@link HmacSignatureVerifier}；请求体读取与 IP 白名单校验、
 * 以及 App Secret 的解密比对标记为 TODO——Servlet 里重复读取 body 需要
 * ContentCachingRequestWrapper，为保持这个拦截器职责单一，body 缓存包装与完整签名比对
 * 放在 core/docs/ROADMAP.md 的收尾任务里，不在此处用一个"能跑但不完整"的实现掩盖这个已知缺口。</p>
 */
@Component
public class HmacAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_TENANT_ID = "ycsopen.tenantId";

    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final HmacSignatureVerifier hmacSignatureVerifier;

    public HmacAuthInterceptor(TenantApiKeyRepository tenantApiKeyRepository,
                                HmacSignatureVerifier hmacSignatureVerifier) {
        this.tenantApiKeyRepository = tenantApiKeyRepository;
        this.hmacSignatureVerifier = hmacSignatureVerifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
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

        // TODO: 用 ContentCachingRequestWrapper 读取 body 后拼接完整待签名串，解密 apiKey 的
        // appSecretEncrypted 再校验签名；当前先只做"密钥存在 + 时间戳 + nonce"三项。

        request.setAttribute(ATTR_TENANT_ID, apiKey.getTenantId());
        return true;
    }
}
