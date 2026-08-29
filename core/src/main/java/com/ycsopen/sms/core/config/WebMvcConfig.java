package com.ycsopen.sms.core.config;

import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final HmacAuthInterceptor hmacAuthInterceptor;

    public WebMvcConfig(HmacAuthInterceptor hmacAuthInterceptor) {
        this.hmacAuthInterceptor = hmacAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(hmacAuthInterceptor).addPathPatterns("/api/v1/sms/**");
    }
}
