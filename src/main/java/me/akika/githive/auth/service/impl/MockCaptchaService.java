package me.akika.githive.auth.service.impl;

import me.akika.githive.auth.dto.CaptchaChallengeResponse;
import me.akika.githive.auth.dto.CaptchaVerifyResponse;
import me.akika.githive.auth.service.CaptchaService;
import me.akika.githive.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockCaptchaService implements CaptchaService {

    private static final String MOCK_CAPTCHA_CODE = "123456";
    private static final long EXPIRE_MINUTES = 5;
    private static final String HINT = "占位验证码已启用，当前固定验证码为 123456";

    private final ConcurrentHashMap<String, CaptchaChallenge> challengeStore = new ConcurrentHashMap<>();

    @Override
    public CaptchaChallengeResponse createChallenge() {
        String captchaKey = "mock-captcha-" + UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRE_MINUTES);
        challengeStore.put(captchaKey, new CaptchaChallenge(MOCK_CAPTCHA_CODE, expiresAt));
        return CaptchaChallengeResponse.builder()
                .captchaKey(captchaKey)
                .hint(HINT)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public CaptchaVerifyResponse verify(String captchaKey, String captchaCode) {
        CaptchaChallenge challenge = challengeStore.get(captchaKey);
        if (challenge == null) {
            return CaptchaVerifyResponse.builder()
                    .passed(false)
                    .message("验证码不存在或已失效")
                    .build();
        }
        if (challenge.isExpired()) {
            challengeStore.remove(captchaKey);
            return CaptchaVerifyResponse.builder()
                    .passed(false)
                    .message("验证码已过期")
                    .build();
        }

        boolean passed = StringUtils.equals(challenge.expectedCode, StringUtils.trim(captchaCode));
        return CaptchaVerifyResponse.builder()
                .passed(passed)
                .message(passed ? "验证码校验通过" : "验证码错误")
                .expiresAt(challenge.expiresAt)
                .build();
    }

    @Override
    public void verifyOrThrow(String captchaKey, String captchaCode, boolean consumeOnSuccess) {
        CaptchaVerifyResponse response = verify(captchaKey, captchaCode);
        if (!response.isPassed()) {
            throw new BusinessException(response.getMessage());
        }
        if (consumeOnSuccess) {
            challengeStore.remove(captchaKey);
        }
    }

    @Override
    public void consume(String captchaKey) {
        challengeStore.remove(captchaKey);
    }

    private record CaptchaChallenge(String expectedCode, LocalDateTime expiresAt) {
        private boolean isExpired() {
            return expiresAt.isBefore(LocalDateTime.now());
        }
    }
}
