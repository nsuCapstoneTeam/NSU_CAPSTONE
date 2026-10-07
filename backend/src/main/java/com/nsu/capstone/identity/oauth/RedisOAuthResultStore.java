package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisOAuthResultStore implements OAuthResultStore {

    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;
    private final OAuthOpaqueValueService opaqueValueService;
    private final OAuthProperties properties;

    public RedisOAuthResultStore(
        StringRedisTemplate redisTemplate,
        OAuthOpaqueValueService opaqueValueService,
        OAuthProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.opaqueValueService = opaqueValueService;
        this.properties = properties;
    }

    @Override
    public String saveLogin(UUID userId) {
        return save(OAuthResult.Type.LOGIN + SEPARATOR + userId);
    }

    @Override
    public String saveSignupRequired(String oauthSignupSessionId) {
        return save(OAuthResult.Type.SIGNUP_REQUIRED + SEPARATOR + oauthSignupSessionId);
    }

    @Override
    public String saveError(ErrorCode errorCode) {
        return save(OAuthResult.Type.ERROR + SEPARATOR + errorCode.name());
    }

    @Override
    public Optional<OAuthResult> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().getAndDelete(
            OAuthRedisKeys.result(opaqueValueService.digest(code))
        );
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(parse(value));
    }

    private String save(String value) {
        String code = opaqueValueService.generate();
        redisTemplate.opsForValue().set(
            OAuthRedisKeys.result(opaqueValueService.digest(code)),
            value,
            properties.getResultTtl()
        );
        return code;
    }

    private OAuthResult parse(String value) {
        String[] parts = value.split(SEPARATOR, 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid OAuth result value");
        }
        OAuthResult.Type type = OAuthResult.Type.valueOf(parts[0]);
        return switch (type) {
            case LOGIN -> OAuthResult.login(UUID.fromString(parts[1]));
            case SIGNUP_REQUIRED -> OAuthResult.signupRequired(parts[1]);
            case ERROR -> OAuthResult.error(ErrorCode.valueOf(parts[1]));
        };
    }
}
