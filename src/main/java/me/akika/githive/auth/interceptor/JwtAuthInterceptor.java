package me.akika.githive.auth.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.akika.githive.auth.annotation.Public;
import me.akika.githive.auth.annotation.RequireRole;
import me.akika.githive.auth.context.AuthContext;
import me.akika.githive.auth.context.LoginUser;
import me.akika.githive.auth.jwt.JwtTokenProvider;
import me.akika.githive.common.exception.AuthenticationException;
import me.akika.githive.common.exception.AuthorizationException;
import me.akika.githive.common.state.SystemRole;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Optional;

/**
 * JWT 认证核心拦截器。
 * <p>
 * 处理流程：
 * <ol>
 *     <li>检查接口是否标注了 {@link Public} — 若是，仍尝试解析令牌（支持可选认证），但不强制要求。</li>
 *     <li>从 Authorization 请求头中提取 Bearer 令牌。</li>
 *     <li>通过 {@link JwtTokenProvider} 解析并验证 JWT。</li>
 *     <li>将已认证用户信息填充到 {@link AuthContext}。</li>
 *     <li>若存在 {@link RequireRole} 注解，校验用户角色。</li>
 *     <li>在 afterCompletion 中清理上下文，防止 ThreadLocal 泄漏。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // 非 Controller （静态资源等）直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean isPublic = isPublicEndpoint(handlerMethod);
        String token = extractToken(request);

        // 无论公开与否，都尝试解析令牌（支持公开接口的可选认证场景）
        if (token != null) {
            Optional<Claims> claimsOpt = jwtTokenProvider.parseToken(token);
            claimsOpt.ifPresent(claims -> {
                LoginUser loginUser = LoginUser.builder()
                        .userId(Long.valueOf(claims.getSubject()))
                        .username(claims.get("username", String.class))
                        .role(SystemRole.valueOf(claims.get("role", String.class)))
                        .build();
                AuthContext.setCurrentUser(loginUser);
            });

            // 令牌无效且为受保护接口 — 拒绝访问
            if (claimsOpt.isEmpty() && !isPublic) {
                throw new AuthenticationException("身份认证失败，令牌无效或已过期");
            }
        } else if (!isPublic) {
            // 受保护接口未携带令牌
            throw new AuthenticationException("未提供身份认证令牌");
        }

        // 角色校验
        checkRoleIfRequired(handlerMethod);

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        AuthContext.clear();
    }

    /**
     * 判断处理方法或其所属 Controller 类是否标注了 {@link Public}。
     */
    private boolean isPublicEndpoint(HandlerMethod handlerMethod) {
        // 方法级注解优先
        if (handlerMethod.hasMethodAnnotation(Public.class)) {
            return true;
        }
        // 类级注解
        return handlerMethod.getBeanType().isAnnotationPresent(Public.class);
    }

    /**
     * 从 Authorization 请求头中提取 Bearer 令牌。
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * 若方法或类上存在 {@link RequireRole} 注解，校验当前用户的角色。
     */
    private void checkRoleIfRequired(HandlerMethod handlerMethod) {
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return;
        }

        LoginUser currentUser = AuthContext.currentUser()
                .orElseThrow(() -> new AuthenticationException("未提供身份认证令牌"));

        SystemRole[] allowedRoles = requireRole.value();
        boolean hasRole = Arrays.asList(allowedRoles).contains(currentUser.role());
        if (!hasRole) {
            throw new AuthorizationException("权限不足，无法访问该资源");
        }
    }
}
