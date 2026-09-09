package com.ycsopen.sms.core.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Applies the complete HMAC check before any SMS API body reaches business code. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HmacSmsAuthenticationFilter extends OncePerRequestFilter {
    private final HmacRequestAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public HmacSmsAuthenticationFilter(HmacRequestAuthenticator authenticator, ObjectMapper objectMapper) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/sms/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request, body);
        try {
            authenticator.authenticate(cached, body);
            filterChain.doFilter(cached, response);
        } catch (HmacRequestAuthenticator.HmacAuthenticationException failure) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED, failure.getMessage()));
        }
    }
}
