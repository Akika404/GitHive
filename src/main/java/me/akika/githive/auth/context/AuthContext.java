package me.akika.githive.auth.context;

import java.util.Optional;

/**
 * 当前已认证用户的 ThreadLocal 持有者。
 * <p>
 * 由 {@link me.akika.githive.auth.interceptor.JwtAuthInterceptor} 在请求处理前填充，
 * 在请求完成后（afterCompletion）清理。
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    /**
     * 设置当前请求线程的已认证用户
     */
    public static void setCurrentUser(LoginUser user) {
        CURRENT_USER.set(user);
    }

    /**
     * 获取当前已认证用户，未认证时返回 empty
     */
    public static Optional<LoginUser> currentUser() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    /**
     * 获取当前用户，未认证时抛出异常。
     * 适用于确定在认证保护下的 Service 层调用。
     */
    public static LoginUser requiredCurrentUser() {
        return currentUser().orElseThrow(
                () -> new IllegalStateException("当前上下文中无已认证用户，不应在 @Public 接口中调用此方法")
        );
    }

    /**
     * 清理上下文。必须在 afterCompletion 中调用以防止 ThreadLocal 泄漏。
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
