package com.nsu.capstone.event.domain;

import static com.nsu.capstone.event.EventContentFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventContentFixtures.Builder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 분위기·템포·보컬의 모든 조합이 서로 다른 본문 해시와 지문을 만드는지 본다(ADR event_management/05). 이름표
 * 구역 규칙(같은 이름표 두 번, 이름표 뒤 위치 값)은 {@link CanonicalDigestTest}가, 고르지 않은 본문의 저장
 * 형식은 {@link EventContentRequestHashTest}의 golden이 고정한다.
 */
@DisplayName("음악 조건 조합별 해시·지문")
class MusicConditionDigestTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    /** 고르지 않음(null)을 포함한 한 축의 값 목록. */
    private static <E extends Enum<E>> List<E> withNotChosen(Class<E> type) {
        List<E> values = new ArrayList<>();
        values.add(null);
        values.addAll(Arrays.asList(type.getEnumConstants()));
        return values;
    }

    private static List<EventContent> allCombinations() {
        List<EventContent> contents = new ArrayList<>();
        for (Mood mood : withNotChosen(Mood.class)) {
            for (Tempo tempo : withNotChosen(Tempo.class)) {
                for (Vocal vocal : withNotChosen(Vocal.class)) {
                    contents.add(with((Builder b) -> {
                        b.mood = mood;
                        b.tempo = tempo;
                        b.vocal = vocal;
                    }));
                }
            }
        }
        return contents;
    }

    private static Set<String> distinct(List<EventContent> contents, Function<EventContent, String> digest) {
        Set<String> values = new HashSet<>();
        contents.forEach(content -> values.add(digest.apply(content)));
        return values;
    }

    @Test
    @DisplayName("세 축의 조합은 고르지 않음을 포함해 분위기 x 템포 x 보컬 개수만큼 있다")
    void coversEveryCombinationIncludingNotChosen() {
        int expected = (Mood.values().length + 1) * (Tempo.values().length + 1) * (Vocal.values().length + 1);

        assertThat(allCombinations()).hasSize(expected);
    }

    @Test
    @DisplayName("모든 조합의 본문 해시가 서로 다르다")
    void everyCombinationHasItsOwnRequestHash() {
        List<EventContent> contents = allCombinations();

        assertThat(distinct(contents, EventContent::requestHash)).hasSameSizeAs(contents);
    }

    @Test
    @DisplayName("모든 조합의 지문이 서로 다르다")
    void everyCombinationHasItsOwnFingerprint() {
        List<EventContent> contents = allCombinations();

        Set<String> fingerprints = distinct(contents, content -> EventDigests.recommendationFingerprint(
                Event.register(UUID.randomUUID(), UUID.randomUUID(), content, CLOCK)));

        assertThat(fingerprints).hasSameSizeAs(contents);
    }
}
