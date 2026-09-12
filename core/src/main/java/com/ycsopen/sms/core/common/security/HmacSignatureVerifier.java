package com.ycsopen.sms.core.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实现 PRD 9.1 节 HTTP API 鉴权：
 * 待签名串 = HTTPMethod + "\n" + URI + "\n" + QueryString + "\n" + CanonicalHeaders + "\n" + Body，
 * 签名 = Base64(HMAC-SHA256(stringToSign, appSecret))。
 * <p>同时校验时间戳 5 分钟有效期与 nonce 唯一性防重放（F-6.4）。</p>
 * <p>nonce 去重优先使用 Redis + TTL；无 Redis 的窄单元测试场景回退到进程内集合。</p>
 */
@Component
public class HmacSignatureVerifier {

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300; // 5 分钟，见 F-6.4
    private final Set<String> seenNonces = ConcurrentHashMap.newKeySet();
    private final StringRedisTemplate redis;

    public HmacSignatureVerifier() {
        this.redis = null;
    }

    @Autowired
    public HmacSignatureVerifier(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis.getIfAvailable();
    }

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
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        if (redis != null) {
            Boolean recorded = redis.opsForValue().setIfAbsent(
                    "hmac-nonce:" + nonce, "1", Duration.ofSeconds(TIMESTAMP_TOLERANCE_SECONDS));
            return Boolean.TRUE.equals(recorded);
        }
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
