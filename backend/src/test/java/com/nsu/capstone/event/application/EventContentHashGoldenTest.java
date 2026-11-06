package com.nsu.capstone.event.application;

import static com.nsu.capstone.event.EventContentFixtures.sample;
import static com.nsu.capstone.event.EventContentFixtures.sampleWithDetails;
import static com.nsu.capstone.event.EventContentFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.Genre;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 리팩터링 전 EventContentHash.of가 만드는 request_hash 값을 고정하는 golden 테스트(NSU-138 0단계).
 * 값은 수정하지 않은 코드를 실제로 실행해 얻었다. 저장된 값이 바뀌면 같은 멱등 키로 다시 온 요청이 422가 된다.
 */
@DisplayName("등록 본문 해시 golden 값")
class EventContentHashGoldenTest {

    static Stream<Arguments> golden() {
        return Stream.of(
            Arguments.of("03 §10.1 기본값, details 없음", sample(),
                "d630350efbf192897b58adc50615bb0a6c609f9a8c1c1716fb5072ca4e10a84a"),
            Arguments.of("validBody()와 같은 내용(details 포함)", sampleWithDetails(),
                "e15358b78af180c845c0701352126e8e69109d70a64b70c35c342a3c0922371f"),
            Arguments.of("artistType null은 -1;", with(b -> b.artistType = null),
                "5045d036aa9564a570b4b005152f00347d5b37def447c2cdcc6601bcd2f07ff1"),
            Arguments.of("장르 [ROCK, POP]은 [POP, ROCK]과 같다",
                with(b -> b.genres = List.of(Genre.ROCK, Genre.POP)),
                "d630350efbf192897b58adc50615bb0a6c609f9a8c1c1716fb5072ca4e10a84a"),
            Arguments.of("장르 3개, 입력 순서 [TROT, ROCK, POP]",
                with(b -> b.genres = List.of(Genre.TROT, Genre.ROCK, Genre.POP)),
                "2c70f80230daf218341cbe32d50f30d7f4665fa87f2bae44fce4d0cea4a2c82a"),
            Arguments.of("장르 1개 [JAZZ]는 앞쉼표", with(b -> b.genres = List.of(Genre.JAZZ)),
                "0f670330f47d2ab0d74c260a5eaef635aaaca966c1e4c92b4484847faebcc285"),
            Arguments.of("설명에 이모지(UTF-16 길이)", with(b -> b.description = "무대 🎵 공연"),
                "a5c0ee2dbef2ec9d30c8b16500e5f86dd28569a48128ca876af59e929597f501"),
            Arguments.of("선택정보 값에 구분자 ';'", with(b -> b.details =
                    new EventDetails("가;나", "다", null, null, null, null, null, null, null, null)),
                "3fb7e5ca2da2be09b849c135f5cf924ee17f42225655437dfc0cadd6350dcd7f"),
            Arguments.of("불리언 true/false/null 섞음", with(b -> {
                    b.artistType = ArtistType.DJ;
                    b.genres = List.of(Genre.ELECTRONIC);
                    b.details = new EventDetails(null, null, true, null, null, false, true, false, true, null);
                }),
                "db385ec2c7cef8ce614f1b25b1094b26e80697c20bc01404537807a2dbc9fc3a"),
            // 불변식을 생성자로 옮기면 빈 문자열 선택정보를 가진 EventContent는 만들어지지 않는다. 그 전까지만 고정한다.
            Arguments.of("[생성자 정규화 전까지] rehearsal = \"\"는 0:;", with(b -> b.details =
                    new EventDetails("", null, null, null, null, null, null, null, null, null)),
                "c1c529d602a8646dffa64c15e528f1076dcdac422e92deda84e9f827b083e71c")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("golden")
    @DisplayName("저장되는 request_hash 값이 바뀌지 않았다")
    void producesTheStoredValue(String label, EventContent content, String expected) {
        assertThat(EventContentHash.of(content)).isEqualTo(expected);
    }
}
