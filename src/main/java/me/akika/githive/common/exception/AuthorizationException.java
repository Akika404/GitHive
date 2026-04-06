package me.akika.githive.common.exception;

/**
 * 已认证用户缺少所需权限时抛出。
 * 对应 HTTP 403 Forbidden。
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(String message) {
        super(message);
    }
}
