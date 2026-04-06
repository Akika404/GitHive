package me.akika.githive.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.akika.githive.auth.config.AuthProperties;
import me.akika.githive.auth.dto.AuthTokenResponse;
import me.akika.githive.auth.dto.AuthUserResponse;
import me.akika.githive.auth.dto.LoginRequest;
import me.akika.githive.auth.dto.LogoutRequest;
import me.akika.githive.auth.dto.RefreshTokenRequest;
import me.akika.githive.auth.dto.RegisterRequest;
import me.akika.githive.auth.entity.AppUser;
import me.akika.githive.auth.entity.UserRefreshToken;
import me.akika.githive.auth.mapper.AppUserMapper;
import me.akika.githive.auth.service.CaptchaService;
import me.akika.githive.auth.mapper.UserRefreshTokenMapper;
import me.akika.githive.auth.service.AuthService;
import me.akika.githive.auth.jwt.JwtTokenProvider;
import me.akika.githive.common.exception.BusinessException;
import me.akika.githive.namespace.service.NamespaceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import me.akika.githive.common.state.SystemRole;
import me.akika.githive.common.state.UserState;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserMapper appUserMapper;
    private final UserRefreshTokenMapper userRefreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthProperties authProperties;
    private final CaptchaService captchaService;
    private final NamespaceService namespaceService;

    @Override
    @Transactional
    public AuthUserResponse register(RegisterRequest request) {
        String username = StringUtils.trim(request.getUsername());
        String email = StringUtils.lowerCase(StringUtils.trim(request.getEmail()));
        String displayName = StringUtils.defaultIfBlank(StringUtils.trim(request.getDisplayName()), username);

        validateRegisterRequest(username, email);
        captchaService.verifyOrThrow(request.getCaptchaKey(), request.getCaptchaCode(), false);

        LocalDateTime now = LocalDateTime.now();
        AppUser user = AppUser.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(displayName)
                .systemRole(SystemRole.SYSTEM_USER)
                .status(UserState.ACTIVE)
                .emailVerified(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        appUserMapper.insert(user);

        initializeUserNamespace(user);
        captchaService.consume(request.getCaptchaKey());
        return toAuthUserResponse(user);
    }

    @Override
    @Transactional
    public AuthTokenResponse login(LoginRequest request, HttpServletRequest httpServletRequest) {
        AppUser user = findLoginUser(StringUtils.trim(request.getIdentifier()));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名/邮箱或密码错误");
        }
        if (user.getStatus() != UserState.ACTIVE) {
            throw new BusinessException("账号不可用");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(user);

        return issueTokenPair(user, httpServletRequest);
    }

    @Override
    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request, HttpServletRequest httpServletRequest) {
        UserRefreshToken storedToken = userRefreshTokenMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getTokenHash, sha256(request.getRefreshToken()))
                        .isNull(UserRefreshToken::getRevokedAt)
        );
        if (storedToken == null) {
            throw new BusinessException("refreshToken 无效");
        }
        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("refreshToken 已过期");
        }

        AppUser user = appUserMapper.selectById(storedToken.getUserId());
        if (user == null || user.getDeletedAt() != null || user.getStatus() != UserState.ACTIVE) {
            throw new BusinessException("账号不可用");
        }

        storedToken.setRevokedAt(LocalDateTime.now());
        storedToken.setLastUsedAt(LocalDateTime.now());
        storedToken.setUpdatedAt(LocalDateTime.now());
        userRefreshTokenMapper.updateById(storedToken);

        return issueTokenPair(user, httpServletRequest);
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        UserRefreshToken storedToken = userRefreshTokenMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getTokenHash, sha256(request.getRefreshToken()))
                        .isNull(UserRefreshToken::getRevokedAt)
        );
        if (storedToken == null) {
            return;
        }
        storedToken.setRevokedAt(LocalDateTime.now());
        storedToken.setUpdatedAt(LocalDateTime.now());
        userRefreshTokenMapper.updateById(storedToken);
    }

    private AppUser findLoginUser(String identifier) {
        AppUser user = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>()
                        .and(wrapper -> wrapper.eq(AppUser::getUsername, identifier)
                                .or()
                                .eq(AppUser::getEmail, identifier))
                        .isNull(AppUser::getDeletedAt)
        );
        if (user == null) {
            throw new BusinessException("用户名/邮箱或密码错误");
        }
        return user;
    }

    private void validateRegisterRequest(String username, String email) {
        if (StringUtils.containsWhitespace(username)) {
            throw new BusinessException("用户名不能包含空白字符");
        }

        boolean usernameExists = appUserMapper.selectCount(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getUsername, username)
                        .isNull(AppUser::getDeletedAt)
        ) > 0;
        if (usernameExists) {
            throw new BusinessException("用户名已存在");
        }

        // 用户名和组织名共享 namespace 空间，需要额外检查 namespace 唯一性
        if (namespaceService.existsByPath(username)) {
            throw new BusinessException("该名称已被占用");
        }

        boolean emailExists = appUserMapper.selectCount(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getEmail, email)
                        .isNull(AppUser::getDeletedAt)
        ) > 0;
        if (emailExists) {
            throw new BusinessException("邮箱已被注册");
        }
    }

    private AuthTokenResponse issueTokenPair(AppUser user, HttpServletRequest httpServletRequest) {
        LocalDateTime accessTokenExpiresAt = LocalDateTime.now().plusMinutes(authProperties.getAccessTokenExpireMinutes());
        LocalDateTime refreshTokenExpiresAt = LocalDateTime.now().plusDays(authProperties.getRefreshTokenExpireDays());

        String accessToken = jwtTokenProvider.generateAccessToken(user, accessTokenExpiresAt);
        String refreshToken = generateRefreshToken();

        LocalDateTime now = LocalDateTime.now();
        UserRefreshToken refreshTokenEntity = UserRefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(refreshToken))
                .userAgent(httpServletRequest.getHeader("User-Agent"))
                .createdIp(resolveClientIp(httpServletRequest))
                .expiresAt(refreshTokenExpiresAt)
                .lastUsedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRefreshTokenMapper.insert(refreshTokenEntity);

        return AuthTokenResponse.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresAt(accessTokenExpiresAt)
                .refreshTokenExpiresAt(refreshTokenExpiresAt)
                .user(toAuthUserResponse(user))
                .build();
    }

    private AuthUserResponse toAuthUserResponse(AppUser user) {
        return AuthUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .systemRole(user.getSystemRole())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    private void initializeUserNamespace(AppUser user) {
        namespaceService.createUserNamespace(user);
    }

    private String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return StringUtils.trim(StringUtils.substringBefore(forwardedFor, ","));
        }
        return request.getRemoteAddr();
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
