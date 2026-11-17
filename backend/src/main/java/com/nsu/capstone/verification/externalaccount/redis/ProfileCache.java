package com.nsu.capstone.verification.externalaccount.redis;

import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 플랫폼이 알려 준 표시 정보와 Spotify 존재 확인 결과의 짧은 캐시(ADR 12번). 1시간 안에 만료되고, 연결을 해제하면 지운다.
 * 약관이 최신 값 표시와 저장 기간 제한을 요구해서 DB에는 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ProfileCache {

    public static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Optional<ProviderProfile> get(ExternalAccountProvider provider, String kind, String providerId) {
        String value = redis.opsForValue().get(RedisKeys.profile(provider, kind, providerId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, ProviderProfile.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    public void evict(ExternalAccountProvider provider, String kind, String providerId) {
        redis.delete(RedisKeys.profile(provider, kind, providerId));
    }

    public void put(ExternalAccountProvider provider, String kind, String providerId, ProviderProfile profile) {
        redis.opsForValue()
                .set(RedisKeys.profile(provider, kind, providerId), objectMapper.writeValueAsString(profile), TTL);
    }
}
