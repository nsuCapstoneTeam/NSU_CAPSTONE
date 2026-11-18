package com.nsu.capstone.verification.externalaccount.redis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeCandidate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 작업물 후보 응답 전체의 캐시(06 §7, ADR 15번). 계정마다 10분 둔다. 후보 조회와 고르기 검증이 함께 쓴다.
 * {@code isSelected}는 담지 않는다. 응답 때 DB로 채운다. 연결을 해제하면 지운다.
 */
@Component
@RequiredArgsConstructor
public class CandidateCache {

    public static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Optional<List<YoutubeCandidate>> get(UUID externalAccountId) {
        String value = redis.opsForValue().get(RedisKeys.candidates(externalAccountId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, Entry.class).candidates());
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    public void put(UUID externalAccountId, List<YoutubeCandidate> candidates) {
        redis.opsForValue()
                .set(
                        RedisKeys.candidates(externalAccountId),
                        objectMapper.writeValueAsString(new Entry(candidates)),
                        TTL);
    }

    public void evict(UUID externalAccountId) {
        redis.delete(RedisKeys.candidates(externalAccountId));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Entry(List<YoutubeCandidate> candidates) {}
}
