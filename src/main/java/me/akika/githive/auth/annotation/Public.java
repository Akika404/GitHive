package me.akika.githive.auth.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Controller 方法或类为公开接口（无需认证）。
 * <p>
 * 默认情况下，所有接口都需要有效的 JWT 令牌。使用此注解可以显式跳过认证校验
 * （如登录、注册等接口）。
 * <p>
 * 可标注在：
 * <ul>
 *     <li>方法级 — 仅该接口公开</li>
 *     <li>类级 — 该 Controller 下所有接口公开</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Public {
}
