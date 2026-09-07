package com.ycsopen.sms.core.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.time.Instant;
import java.util.UUID;

/** 控制台登录会话令牌（JWT + RBAC，见 PRD 3.2 / 6.2 节）。 */
@Component
public class JwtTokenProvider {

    private final Key signingKey;
    private final long accessTokenTtlMillis;

    public JwtTokenProvider(@Value("${ycsopen.security.jwt.secret}") String secret,
                             @Value("${ycsopen.security.jwt.access-token-ttl-minutes}") long ttlMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMillis = ttlMinutes * 60_000L;
    }

    public String generateToken(Long userId, String userType, Long tenantId) {
        return issueToken(userId, userType, tenantId).token();
    }

    public IssuedToken issueToken(Long userId, String userType, Long tenantId) {
        Date now = new Date();
        String sessionId = UUID.randomUUID().toString();
        Date expiresAt = new Date(now.getTime() + accessTokenTtlMillis);
        var builder = Jwts.builder()
                .id(sessionId)
                .subject(String.valueOf(userId))
                .claim("userType", userType)
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(expiresAt);
        return new IssuedToken(builder.signWith(signingKey, SignatureAlgorithm.HS256).compact(),
                sessionId, expiresAt.toInstant());
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) signingKey).build()
                .parseSignedClaims(token).getPayload();
    }

    public record IssuedToken(String token, String sessionId, Instant expiresAt) { }
}
