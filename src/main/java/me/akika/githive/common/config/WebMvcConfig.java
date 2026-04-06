package me.akika.githive.common.config;

import lombok.RequiredArgsConstructor;
import me.akika.githive.auth.interceptor.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 统一配置。
 * <p>
 * 注册：
 * <ul>
 *     <li>{@link JwtAuthInterceptor} — 拦截所有 /api/** 请求进行 JWT 认证</li>
 * </ul>
 * <p>
 * 静态资源和框架路径（Swagger、actuator、error）通过路径模式排除拦截。
 * 接口级别的访问控制使用 {@code @Public} 注解。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // Swagger / OpenAPI / Knife4j 文档路径
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/doc.html",
                        "/webjars/**",
                        // Spring Boot 错误页面
                        "/error"
                );
    }
}
