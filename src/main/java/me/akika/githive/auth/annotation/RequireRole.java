package me.akika.githive.auth.annotation;

import me.akika.githive.common.state.SystemRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求已认证用户具有指定角色之一才可访问。
 * <p>
 * 隐含认证要求 — 无需额外添加认证注解。
 * 未认证时返回 401；已认证但角色不匹配时返回 403。
 * <p>
 * 示例：
 * <pre>
 * &#64;RequireRole(SystemRole.SYSTEM_ADMIN)
 * &#64;GetMapping("/admin/users")
 * public ApiResponse&lt;List&lt;UserVO&gt;&gt; listUsers() { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 允许的角色列表（OR 逻辑 — 满足其中任一角色即可访问）。
     */
    SystemRole[] value();
}
