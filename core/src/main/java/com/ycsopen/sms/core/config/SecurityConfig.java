package com.ycsopen.sms.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * PRD 6.2 节安全要求的最小落地：控制台走 JWT（无状态会话），HTTP 开放 API（/api/v1/**）
 * 走独立的 HMAC 拦截器（见 web.interceptor.HmacAuthInterceptor），不复用 Spring Security 的会话体系。
 * <p>bcrypt 而非 MD5+盐——见 PRD 6.2.1 表格里明确写的那句话。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/auth/**", "/api/v1/sms/**", "/actuator/health").permitAll()
                    // TODO: 补齐 JWT 过滤器后，其余 /api/v1/console/** 路径改为 .authenticated()
                    // 并按 3.2 节 RBAC 模型接入方法级 @PreAuthorize —— 当前先全部放开，
                    // 便于先验证业务流程，安全收尾任务见 core/docs/ROADMAP.md。
                    .anyRequest().permitAll());
        return http.build();
    }
}
