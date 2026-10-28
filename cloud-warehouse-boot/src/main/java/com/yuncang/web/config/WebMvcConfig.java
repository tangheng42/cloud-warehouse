package com.yuncang.web.config;

import com.yuncang.web.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) { this.authInterceptor = authInterceptor; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/api/v1/**",
                        "/api/v1.1/**",
                        "/api/learning/**"
                )
                .excludePathPatterns("/api/v1/auth/login");
        // /doc.html、/v3/api-docs、/swagger-ui/** 及静态资源不在 /api/v1/** 内。
    }
}
