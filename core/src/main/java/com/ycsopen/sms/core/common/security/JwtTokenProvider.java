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
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userType", userType)
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenTtlMillis));
        return builder.signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) signingKey).build()
                .parseSignedClaims(token).getPayload();
    }
}
