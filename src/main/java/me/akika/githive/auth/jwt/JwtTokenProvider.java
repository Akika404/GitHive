package me.akika.githive.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.akika.githive.auth.config.AuthProperties;
import me.akika.githive.auth.entity.AppUser;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AuthProperties authProperties;

    /**
     * 为指定用户签发 JWT 访问令牌。
     */
    public String generateAccessToken(AppUser user, LocalDateTime expiresAt) {
        return Jwts.builder()
                .issuer(authProperties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getSystemRole().name())
                .claim("type", "access")
                .issuedAt(Date.from(LocalDateTime.now().atOffset(ZoneOffset.UTC).toInstant()))
                .expiration(Date.from(expiresAt.atOffset(ZoneOffset.UTC).toInstant()))
                .signWith(signingKey())
                .compact();
    }

    /**
     * 解析并验证 JWT 令牌，校验签名、过期时间和签发者。
     *
     * @return 令牌有效时返回包含 Claims 的 Optional，否则返回 empty
     */
    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(authProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT token is blank or null: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 仅验证令牌有效性，不返回 Claims。适用于快速校验场景。
     */
    public boolean validateToken(String token) {
        return parseToken(token).isPresent();
    }

    /**
     * 从有效令牌中提取用户 ID（即 JWT subject）。
     */
    public Optional<Long> getUserId(String token) {
        return parseToken(token).map(claims -> Long.valueOf(claims.getSubject()));
    }

    /**
     * 从有效令牌中提取用户名。
     */
    public Optional<String> getUsername(String token) {
        return parseToken(token).map(claims -> claims.get("username", String.class));
    }

    /**
     * 从有效令牌中提取角色。
     */
    public Optional<String> getRole(String token) {
        return parseToken(token).map(claims -> claims.get("role", String.class));
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(authProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
