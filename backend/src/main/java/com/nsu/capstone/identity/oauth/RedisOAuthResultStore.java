package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisOAuthResultStore implements OAuthResultStore {

    private static final Logger log = LoggerFactory.getLogger(RedisOAuthResultStore.class);
    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;
    private final OAuthOpaqueValueService opaqueValueService;
    private final OAuthProperties properties;

    public RedisOAuthResultStore(
            StringRedisTemplate redisTemplate, OAuthOpaqueValueService opaqueValueService, OAuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.opaqueValueService = opaqueValueService;
        this.properties = properties;
    }

    // 값 형식: TYPE:bindingDigest:payload (digest는 16진수라 구분자를 포함하지 않는다)
    @Override
    public String saveLogin(UUID userId, String bindingDigest) {
        return save(OAuthResult.Type.LOGIN, bindingDigest, userId.toString());
    }

    @Override
    public String saveSignupRequired(String oauthSignupSessionId, String bindingDigest) {
        return save(OAuthResult.Type.SIGNUP_REQUIRED, bindingDigest, oauthSignupSessionId);
    }

    @Override
    public String saveError(ErrorCode errorCode, String bindingDigest) {
        return save(OAuthResult.Type.ERROR, bindingDigest, errorCode.name());
    }

    @Override
    public Optional<OAuthResult> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().getAndDelete(OAuthRedisKeys.result(opaqueValueService.digest(code)));
        if (value == null) {
            return Optional.empty();
        }
        return parse(value);
    }

    private String save(OAuthResult.Type type, String bindingDigest, String payload) {
        if (bindingDigest == null || bindingDigest.isBlank()) {
            throw new IllegalArgumentException("결합 digest가 필요하다");
        }
        String code = opaqueValueService.generate();
        redisTemplate
                .opsForValue()
                .set(
                        OAuthRedisKeys.result(opaqueValueService.digest(code)),
                        type + SEPARATOR + bindingDigest + SEPARATOR + payload,
                        properties.getResultTtl());
        return code;
    }

    /** 읽을 수 없는 값(배포 전 형식, 깨진 값)은 이미 지워졌으므로 일회용 결과가 없는 것과 같게 본다. */
    private Optional<OAuthResult> parse(String value) {
        String[] parts = value.split(SEPARATOR, 3);
        if (parts.length != 3) {
            return unreadable();
        }
        try {
            OAuthResult.Type type = OAuthResult.Type.valueOf(parts[0]);
            String bindingDigest = parts[1];
            return Optional.of(
                    switch (type) {
                        case LOGIN -> OAuthResult.login(UUID.fromString(parts[2]), bindingDigest);
                        case SIGNUP_REQUIRED -> OAuthResult.signupRequired(parts[2], bindingDigest);
                        case ERROR -> OAuthResult.error(ErrorCode.valueOf(parts[2]), bindingDigest);
                    });
        } catch (IllegalArgumentException e) {
            return unreadable();
        }
    }

    private Optional<OAuthResult> unreadable() {
        log.warn("읽을 수 없는 OAuth 결과 값을 폐기했다");
        return Optional.empty();
    }
}
