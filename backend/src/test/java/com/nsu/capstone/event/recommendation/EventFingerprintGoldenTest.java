package com.nsu.capstone.event.recommendation;

import static com.nsu.capstone.event.EventContentFixtures.sample;
import static com.nsu.capstone.event.EventContentFixtures.sampleWithDetails;
import static com.nsu.capstone.event.EventContentFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.Genre;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * isEventChanged 지문의 golden 값(NSU-138 0단계). 수정하지 않은 코드를 실제로 실행해 얻었다.
 * 지문은 아직 저장되지 않지만(NSU-63 전), 저장하기 시작한 뒤 값이 바뀌면 옛 추천이 모두 isEventChanged = true가 된다.
 * artistType이 null인 벡터만 리팩터링 전과 값이 다르다. null 표기를 정규 문자열 구현의 -1;로 통일했기
 * 때문이다(ADR event_management/04 결정 5). 이 벡터는 같은 형식을 독립적으로 계산해 얻었다.
 */
@DisplayName("행사 지문 golden 값")
class EventFingerprintGoldenTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    static Event event(EventContent content) {
        return Event.register(UUID.randomUUID(), UUID.randomUUID(), content, CLOCK);
    }

    static Stream<Arguments> golden() {
        return Stream.of(
            Arguments.of("기본값", sample(),
                "29cc7c165579801bc824c4033c9626047a77e0752ac89c5b96328ad4d80efbb0"),
            Arguments.of("details를 채워도 같다(지문 밖)", sampleWithDetails(),
                "29cc7c165579801bc824c4033c9626047a77e0752ac89c5b96328ad4d80efbb0"),
            Arguments.of("artistType null은 -1;로 적는다", with(b -> b.artistType = null),
                "39605a1595965c3303179ead7ccce8de9721cec81116e6b66b9f6bdb3a3cdbcd"),
            Arguments.of("장르 [ROCK, POP]은 [POP, ROCK]과 같다",
                with(b -> b.genres = List.of(Genre.ROCK, Genre.POP)),
                "29cc7c165579801bc824c4033c9626047a77e0752ac89c5b96328ad4d80efbb0"),
            Arguments.of("장르 3개", with(b -> b.genres = List.of(Genre.TROT, Genre.ROCK, Genre.POP)),
                "8bf3b248d476950715d4f7d0a10323bd66b333a75f1c7fa4f8f68e3705915dea"),
            Arguments.of("장르 1개", with(b -> b.genres = List.of(Genre.JAZZ)),
                "d17cff8ab66bd3be159230c945bc51382d1d63b48cd19e3bbe5e21e28a73ba4c"),
            Arguments.of("설명에 이모지", with(b -> b.description = "무대 🎵 공연"),
                "6cad0dfa4c67222b712bfb5be3ce05b99b2b4177049932bc042201adff126c5a"),
            Arguments.of("DJ + 일렉트로닉", with(b -> {
                    b.artistType = ArtistType.DJ;
                    b.genres = List.of(Genre.ELECTRONIC);
                }),
                "5b413a0d3151103fe041c31f87e5f9e315232cc6bf3343fd8ac3f960f7ceba5b")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("golden")
    @DisplayName("지문 값이 바뀌지 않았다")
    void producesTheSameValueAsBeforeTheRefactoring(String label, EventContent content, String expected) {
        assertThat(EventFingerprint.of(event(content)).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("지문은 Event에 저장된 Instant를 Asia/Seoul 벽시계로 되돌려 쓴다")
    void usesTheSeoulWallClockOfTheStoredInstants() {
        Event event = event(sample()); // 2026-11-06 18:00 KST = 09:00Z로 저장된다

        assertThat(event.getStartAt()).isEqualTo(Instant.parse("2026-11-06T09:00:00Z"));
        assertThat(EventFingerprint.of(event).value())
            .isEqualTo("29cc7c165579801bc824c4033c9626047a77e0752ac89c5b96328ad4d80efbb0");
    }
}
