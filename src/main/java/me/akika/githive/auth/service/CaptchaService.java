package me.akika.githive.auth.service;

import me.akika.githive.auth.dto.CaptchaChallengeResponse;
import me.akika.githive.auth.dto.CaptchaVerifyResponse;

public interface CaptchaService {

    CaptchaChallengeResponse createChallenge();

    CaptchaVerifyResponse verify(String captchaKey, String captchaCode);

    void verifyOrThrow(String captchaKey, String captchaCode, boolean consumeOnSuccess);

    void consume(String captchaKey);
}
