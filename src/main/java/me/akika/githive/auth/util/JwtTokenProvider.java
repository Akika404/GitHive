package me.akika.githive.auth.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import me.akika.githive.auth.config.AuthProperties;
import me.akika.githive.auth.entity.AppUser;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AuthProperties authProperties;

    public String generateAccessToken(AppUser user, LocalDateTime expiresAt) {
        return Jwts.builder()
                .issuer(authProperties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getSystemRole())
                .claim("type", "access")
                .issuedAt(Date.from(LocalDateTime.now().atOffset(ZoneOffset.UTC).toInstant()))
                .expiration(Date.from(expiresAt.atOffset(ZoneOffset.UTC).toInstant()))
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(authProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
