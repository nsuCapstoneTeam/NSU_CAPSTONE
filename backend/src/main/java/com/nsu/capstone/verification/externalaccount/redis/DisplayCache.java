package com.nsu.capstone.verification.externalaccount.redis;

import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeDisplay;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * YouTube 계정의 표시 정보 캐시(06 §6.5, ADR 12번). 계정마다 한 값을 1시간 두고, 받지 못한 결과는 30초 동안 기억해
 * 장애 때 반복 호출을 막는다. 값은 고른 영상 목록에 의존하므로 영상 목록이 바뀌면 쓰지 않는다.
 * 연결을 해제하면 지운다.
 */
@Component
@RequiredArgsConstructor
public class DisplayCache {

    public static final Duration TTL = Duration.ofHours(1);
    public static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 같은 영상 목록으로 받은 값이 있으면 돌려준다. */
    public Optional<YoutubeDisplay> get(UUID externalAccountId, List<String> videoIds) {
        String value = redis.opsForValue().get(RedisKeys.display(externalAccountId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            Entry entry = objectMapper.readValue(value, Entry.class);
            return entry.videoIds().equals(videoIds)
                    ? Optional.of(new YoutubeDisplay(entry.channel(), entry.publicVideos()))
                    : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    public void put(UUID externalAccountId, List<String> videoIds, YoutubeDisplay display) {
        Entry entry = new Entry(videoIds, display.channel(), display.publicVideos());
        redis.opsForValue().set(RedisKeys.display(externalAccountId), objectMapper.writeValueAsString(entry), TTL);
        redis.delete(RedisKeys.displayFailed(externalAccountId));
    }

    public boolean recentlyFailed(UUID externalAccountId) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKeys.displayFailed(externalAccountId)));
    }

    public void markFailed(UUID externalAccountId) {
        redis.opsForValue().set(RedisKeys.displayFailed(externalAccountId), "1", FAILURE_MEMORY);
    }

    /** 연결을 해제하거나 ID 확인 작업이 지웠을 때 그 계정의 표시 정보를 지운다. */
    public void evict(UUID externalAccountId) {
        redis.delete(List.of(RedisKeys.display(externalAccountId), RedisKeys.displayFailed(externalAccountId)));
    }

    private record Entry(List<String> videoIds, ProviderProfile channel, Map<String, ProviderProfile> publicVideos) {}
}
