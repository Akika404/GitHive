package me.akika.githive.auth.service;

import me.akika.githive.auth.dto.AuthTokenResponse;
import me.akika.githive.auth.dto.LoginRequest;
import me.akika.githive.auth.dto.LogoutRequest;
import me.akika.githive.auth.dto.RefreshTokenRequest;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    AuthTokenResponse login(LoginRequest request, HttpServletRequest httpServletRequest);

    AuthTokenResponse refresh(RefreshTokenRequest request, HttpServletRequest httpServletRequest);

    void logout(LogoutRequest request);
}
