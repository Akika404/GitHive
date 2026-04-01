package me.akika.githive.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LogoutRequest {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
