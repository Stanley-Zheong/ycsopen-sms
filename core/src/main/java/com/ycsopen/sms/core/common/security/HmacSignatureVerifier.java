package com.ycsopen.sms.core.common.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实现 PRD 9.1 节 HTTP API 鉴权：
 * 待签名串 = HTTPMethod + "\n" + URI + "\n" + QueryString + "\n" + CanonicalHeaders + "\n" + Body，
 * 签名 = Base64(HMAC-SHA256(stringToSign, appSecret))。
 * <p>同时校验时间戳 5 分钟有效期与 nonce 唯一性防重放（F-6.4）。</p>
 * <p>nonce 去重这里用内存 Set 仅作演示；生产环境必须换成 Redis + TTL，否则多实例部署下形同虚设——
 * 这是本仓库里明确标记为"仅示例，勿直接上生产"的少数几处之一。</p>
 */
@Component
public class HmacSignatureVerifier {

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300; // 5 分钟，见 F-6.4
    private final Set<String> seenNonces = ConcurrentHashMap.newKeySet(); // DEMO ONLY — replace with Redis in prod

    public String buildStringToSign(String method, String uri, String queryString,
                                     String canonicalHeaders, String body) {
        return method.toUpperCase() + "\n" + uri + "\n" +
                (queryString == null ? "" : queryString) + "\n" +
                canonicalHeaders + "\n" +
                (body == null ? "" : body);
    }

    public String sign(String stringToSign, String appSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("hmac signing failed", e);
        }
    }

    public boolean verifyTimestamp(long timestampEpochSeconds) {
        long now = Instant.now().getEpochSecond();
        return Math.abs(now - timestampEpochSeconds) <= TIMESTAMP_TOLERANCE_SECONDS;
    }

    /** @return true 表示这是第一次见到该 nonce（放行）；false 表示重放攻击，应拒绝。 */
    public boolean checkAndRecordNonce(String nonce) {
        return seenNonces.add(nonce);
    }

    public boolean verify(String providedSignature, String stringToSign, String appSecret) {
        String expected = sign(stringToSign, appSecret);
        // 恒定时间比较，防止时序攻击泄露签名信息
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
    }
}
