package com.example.dynamicdata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 负责配置跨域资源共享(CORS)策略
 * 限制允许访问的域名和HTTP方法，提高安全性
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 从配置文件中读取允许的跨域域名
     * 默认只允许本地开发环境访问
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://localhost:8081}")
    private String[] allowedOrigins;

    /**
     * 配置跨域映射
     * 限制允许的域名、HTTP方法和请求头，提高API安全性
     * 
     * @param registry CORS注册表
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                .allowCredentials(true)
                .maxAge(3600); // 预检请求缓存时间1小时
        
        // GraphQL端点的CORS配置
        registry.addMapping("/graphql")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST")
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}