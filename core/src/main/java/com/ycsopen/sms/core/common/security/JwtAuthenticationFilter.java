package com.ycsopen.sms.core.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates console requests with the access token issued by {@link JwtTokenProvider}.
 * The SMS API is intentionally outside this filter and remains protected by its HMAC interceptor.
 */
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CONSOLE_PATH = "/api/v1/console";
    private static final String CONSOLE_AUTH_PATH = "/api/v1/console/auth";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final JwtAccessVerifier accessVerifier;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, JwtAccessVerifier accessVerifier) {
        this.tokenProvider = tokenProvider;
        this.accessVerifier = accessVerifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean consoleRequest = path.equals(CONSOLE_PATH) || path.startsWith(CONSOLE_PATH + "/");
        boolean loginRequest = path.equals(CONSOLE_AUTH_PATH) || path.startsWith(CONSOLE_AUTH_PATH + "/");
        return !consoleRequest || loginRequest;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
                BEARER_PREFIX.length())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = tokenProvider.parse(token);
            JwtAccessVerifier.VerifiedAccess access = accessVerifier.verify(claims);
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    access.subject(),
                    null,
                    access.authorities());
            authentication.setDetails(claims.getId());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | org.springframework.security.core.AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            reject(response);
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
    }
}
