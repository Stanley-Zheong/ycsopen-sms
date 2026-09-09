package com.ycsopen.sms.core.web.security;

import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository.SignatureAuthenticationProjection;
import com.ycsopen.sms.core.service.routing.ApiKeyRateLimitService.RatePolicy;
import com.ycsopen.sms.core.service.tenant.TenantCredentialSecretProtectionService;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/** Complete F-6.4 HTTP API request authentication: key, timestamp, nonce, IP, secret and body HMAC. */
@Service
public class HmacRequestAuthenticator {
    private final TenantApiKeyRepository apiKeys;
    private final HmacSignatureVerifier signatures;
    private final TenantCredentialSecretProtectionService secrets;

    public HmacRequestAuthenticator(TenantApiKeyRepository apiKeys,
                                    HmacSignatureVerifier signatures,
                                    TenantCredentialSecretProtectionService secrets) {
        this.apiKeys = apiKeys;
        this.signatures = signatures;
        this.secrets = secrets;
    }

    public void authenticate(HttpServletRequest request, byte[] body) {
        String appKey = requiredHeader(request, "X-App-Key");
        String timestamp = requiredHeader(request, "X-Timestamp");
        String nonce = requiredHeader(request, "X-Nonce");
        String providedSignature = requiredHeader(request, "X-Signature");

        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException invalid) {
            throw new HmacAuthenticationException("时间戳不合法");
        }

        SignatureAuthenticationProjection apiKey = apiKeys.findSignatureAuthenticationByAppKey(appKey)
                .orElseThrow(() -> new HmacAuthenticationException("无效的 App Key"));
        if (!signatures.verifyTimestamp(epochSeconds)) {
            throw new HmacAuthenticationException("时间戳超出 5 分钟有效期");
        }
        if (!ipAllowed(apiKey.getIpWhitelist(), request.getRemoteAddr())) {
            throw new HmacAuthenticationException("来源 IP 不在白名单内");
        }
        if (!signatures.checkAndRecordNonce("%d:%s:%s".formatted(apiKey.getId(), timestamp, nonce))) {
            throw new HmacAuthenticationException("nonce 重复，疑似重放攻击");
        }

        char[] secret = secrets.reveal(apiKey.getTenantId(), apiKey.getId(),
                "app_secret_encrypted", apiKey.getAppSecretEncrypted());
        try {
            String stringToSign = signatures.buildStringToSign(request.getMethod(), request.getRequestURI(),
                    canonicalQuery(request.getQueryString()), canonicalHeaders(appKey, timestamp, nonce),
                    new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
            if (!signatures.verify(providedSignature, stringToSign, new String(secret))) {
                throw new HmacAuthenticationException("签名校验失败");
            }
        } finally {
            Arrays.fill(secret, '\0');
        }

        request.setAttribute(HmacAuthInterceptor.ATTR_TENANT_ID, apiKey.getTenantId());
        request.setAttribute(HmacAuthInterceptor.ATTR_API_KEY_ID, apiKey.getId());
        request.setAttribute(HmacAuthInterceptor.ATTR_RATE_POLICY, new RatePolicy(apiKey.getRateLimitPerSec(),
                apiKey.getRateLimitPerMin(), apiKey.getRateLimitPerHour(), apiKey.getRateLimitPerDay()));
        request.setAttribute("ycsopen.requestBodySha256", sha256Hex(body == null ? new byte[0] : body));
    }

    public static String canonicalHeaders(String appKey, String timestamp, String nonce) {
        return "x-app-key:%s\nx-nonce:%s\nx-timestamp:%s".formatted(appKey.trim(), nonce.trim(), timestamp.trim());
    }

    private static String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new HmacAuthenticationException("缺少必需的鉴权请求头");
        }
        return value.trim();
    }

    private static String canonicalQuery(String queryString) {
        return queryString == null ? "" : queryString;
    }

    private static boolean ipAllowed(String whitelist, String remoteAddr) {
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        String normalized = whitelist.replace("[", "").replace("]", "").replace("\"", "");
        for (String entry : normalized.split(",")) {
            String rule = entry.trim();
            if (rule.isEmpty()) continue;
            if (rule.equals(remoteAddr)) return true;
            if (rule.endsWith("/32") && rule.substring(0, rule.length() - 3).equals(remoteAddr)) return true;
            if (rule.contains("/") && ipv4CidrContains(rule, remoteAddr)) return true;
        }
        return false;
    }

    private static boolean ipv4CidrContains(String cidr, String remoteAddr) {
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2) return false;
        try {
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) return false;
            int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
            return (ipv4(parts[0]) & mask) == (ipv4(remoteAddr) & mask);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static int ipv4(String value) {
        String[] octets = value.split("\\.");
        if (octets.length != 4) throw new IllegalArgumentException("invalid ipv4");
        int result = 0;
        for (String octet : octets) {
            int parsed = Integer.parseInt(octet);
            if (parsed < 0 || parsed > 255) throw new IllegalArgumentException("invalid ipv4");
            result = (result << 8) | parsed;
        }
        return result;
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception impossible) {
            throw new IllegalStateException("sha256 unavailable", impossible);
        }
    }

    public static class HmacAuthenticationException extends RuntimeException {
        public HmacAuthenticationException(String message) {
            super(message);
        }
    }
}
