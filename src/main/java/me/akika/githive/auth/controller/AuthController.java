package me.akika.githive.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.akika.githive.auth.dto.CaptchaChallengeResponse;
import me.akika.githive.auth.dto.CaptchaVerifyRequest;
import me.akika.githive.auth.dto.CaptchaVerifyResponse;
import me.akika.githive.auth.dto.AuthTokenResponse;
import me.akika.githive.auth.dto.AuthUserResponse;
import me.akika.githive.auth.dto.LoginRequest;
import me.akika.githive.auth.dto.LogoutRequest;
import me.akika.githive.auth.dto.RefreshTokenRequest;
import me.akika.githive.auth.dto.RegisterRequest;
import me.akika.githive.auth.service.AuthService;
import me.akika.githive.auth.service.CaptchaService;
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
    private final CaptchaService captchaService;

    @PostMapping("/register")
    public ApiResponse<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("注册成功", authService.register(request));
    }

    @PostMapping("/captcha/challenge")
    public ApiResponse<CaptchaChallengeResponse> createCaptchaChallenge() {
        return ApiResponse.success("验证码已生成", captchaService.createChallenge());
    }

    @PostMapping("/captcha/verify")
    public ApiResponse<CaptchaVerifyResponse> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        return ApiResponse.success(captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode()));
    }

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
