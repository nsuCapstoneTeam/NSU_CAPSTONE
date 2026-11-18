package com.nsu.capstone.verification.externalaccount.redis;

import com.nsu.capstone.verification.externalaccount.domain.AuthorizationFlow;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 연결 시작과 연결 요청 사이의 진행 값(ADR "Redis" 표). 키에 연결 요청의 Bearer 사용자 ID가 들어가서,
 * 다른 사용자의 토큰으로 보낸 {@code state}는 키를 찾지 못하고 원래 사용자의 값을 지우지도 못한다.
 */
@Component
@RequiredArgsConstructor
public class AuthorizationStore {

    public static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void save(String stateDigest, UUID artistId, AuthorizationFlow flow) {
        redis.opsForValue()
                .set(RedisKeys.authorization(stateDigest, artistId), objectMapper.writeValueAsString(flow), TTL);
    }

    /** 값을 꺼내며 지운다(GETDEL). 한 번만 쓸 수 있다. 읽을 수 없는 값은 이미 지워졌으므로 없는 것과 같다. */
    public Optional<AuthorizationFlow> consume(String stateDigest, UUID artistId) {
        String value = redis.opsForValue().getAndDelete(RedisKeys.authorization(stateDigest, artistId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, AuthorizationFlow.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }
}
