package me.akika.githive.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "登录标识不能为空")
    private String identifier;

    @NotBlank(message = "密码不能为空")
    private String password;
}
