package me.akika.githive.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class AuthProperties {

    @NotBlank
    private String issuer;

    @NotBlank
    private String secret;

    @Min(1)
    private long accessTokenExpireMinutes;

    @Min(1)
    private long refreshTokenExpireDays;
}
