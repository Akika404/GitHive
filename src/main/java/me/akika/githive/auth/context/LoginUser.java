package me.akika.githive.auth.context;

import lombok.Builder;
import me.akika.githive.common.state.SystemRole;

/**
 * 从 JWT 中提取的已认证用户轻量 DTO。
 * 通过 {@link AuthContext} 在请求生命周期内传递。
 */
@Builder
public record LoginUser(
        Long userId,
        String username,
        SystemRole role
) {
}
