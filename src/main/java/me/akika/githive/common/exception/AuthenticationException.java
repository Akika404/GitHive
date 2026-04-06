package me.akika.githive.common.exception;

/**
 * 请求缺少有效的认证凭据时抛出。
 * 对应 HTTP 401 Unauthorized。
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}
