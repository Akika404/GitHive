package me.akika.githive.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.akika.githive.auth.dto.AuthTokenResponse;
import me.akika.githive.auth.dto.LoginRequest;
import me.akika.githive.auth.dto.LogoutRequest;
import me.akika.githive.auth.dto.RefreshTokenRequest;
import me.akika.githive.auth.service.AuthService;
import me.akika.githive.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpServletRequest) {
        return ApiResponse.success("登录成功", authService.login(request, httpServletRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                  HttpServletRequest httpServletRequest) {
        return ApiResponse.success("刷新成功", authService.refresh(request, httpServletRequest));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.success("退出成功", null);
    }
}
