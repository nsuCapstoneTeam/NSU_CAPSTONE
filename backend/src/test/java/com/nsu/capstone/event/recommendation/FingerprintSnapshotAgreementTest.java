package com.nsu.capstone.event.recommendation;

import static com.nsu.capstone.event.EventContentFixtures.alternate;
import static com.nsu.capstone.event.EventContentFixtures.sampleWithDetails;
import static com.nsu.capstone.event.RecordReflection.names;
import static com.nsu.capstone.event.RecordReflection.rebuildWith;
import static com.nsu.capstone.event.RecordReflection.value;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.Keyphrase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지문(03 §8.3)이 추천 입력(§8.2)과 같은 필드로 만들어졌는지 본다(ADR event_management/04 Consequences).
 * EventSnapshot은 지문을 컴포넌트로 갖기 때문에 record 전체를 비교하면 지문 변화만으로 참이 되어 아무것도
 * 검출하지 못한다. 그래서 추천 입력은 eligibility와 conditions만, 지문은 fingerprint 값만 따로 비교한다.
 *
 * <p>필드를 더하는 사람은 ROLE 표에 한 줄을 더한다. 표의 키 집합은 EventContent의 컴포넌트 집합과 같아야 해서,
 * 빠뜨리면 첫 테스트가 실패한다.
 *
 * <p>추천 입력은 EligibilityInput과 MusicConditions다. AI에 보내는 검색 문장은 conditions에서
 * {@link Keyphrase#of}로만 만들어지므로(ADR recommendation_search_input/00 결정 7), "conditions가 바뀐다"는
 * "검색 문장이 바뀐다"와 같아야 한다. 남는 우회로는 추천 서비스가 Event를 다시 읽어 MusicConditions를 손으로 만드는
 * 것이며, EventAccess 우회처럼 리뷰로 막는다.
 */
@DisplayName("지문과 추천 입력의 필드 일치")
class FingerprintSnapshotAgreementTest {

    enum Role {
        /** EligibilityInput에 실린다. */
        ELIGIBILITY,
        /** MusicConditions에 실린다. 검색 문장을 만드는 재료다. */
        MUSIC_CONDITIONS,
        /** 추천 입력이 아니다. 지문도 바뀌지 않는다. */
        NONE
    }

    /** 03 §8.2(snapshot)·§8.3(지문) 표를 컴포넌트 이름으로 옮긴 것. 추천 입력이면 지문도 바뀐다. */
    static final Map<String, Role> ROLE = Map.ofEntries(
        entry("name", Role.NONE),
        entry("eventType", Role.MUSIC_CONDITIONS),
        entry("start", Role.ELIGIBILITY),
        entry("end", Role.ELIGIBILITY),
        entry("performanceMinutes", Role.ELIGIBILITY),
        entry("venue", Role.NONE),
        entry("regionCode", Role.ELIGIBILITY),
        entry("artistType", Role.ELIGIBILITY),
        entry("genres", Role.MUSIC_CONDITIONS),
        entry("budgetMin", Role.ELIGIBILITY),
        entry("budgetMax", Role.ELIGIBILITY),
        entry("expectedAudience", Role.NONE),
        entry("description", Role.NONE),
        entry("visibility", Role.NONE),
        entry("details", Role.NONE)
    );

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    static EventSnapshot snapshot(EventContent content) {
        return EventSnapshot.of(Event.register(UUID.randomUUID(), UUID.randomUUID(), content, CLOCK));
    }

    @Test
    @DisplayName("표가 EventContent의 모든 컴포넌트를 다룬다")
    void everyComponentIsClassified() {
        assertThat(ROLE.keySet()).containsExactlyInAnyOrderElementsOf(names(EventContent.class));
    }

    @Test
    @DisplayName("컴포넌트를 바꾸면 표가 정한 쪽만 바뀌고, 검색 문장은 조건이, 지문은 추천 입력이 바뀔 때만 바뀐다")
    void eligibilityConditionsKeyphraseAndFingerprintChangeAsTheTableSays() throws Exception {
        EventContent base = sampleWithDetails();
        EventSnapshot before = snapshot(base);

        for (String component : names(EventContent.class)) {
            EventSnapshot after = snapshot(
                rebuildWith(EventContent.class, base, component, value(alternate(), component)));
            Role role = ROLE.get(component);

            boolean eligibilityChanged = !after.eligibility().equals(before.eligibility());
            boolean conditionsChanged = !after.conditions().equals(before.conditions());
            boolean keyphraseChanged = !Keyphrase.of(after.conditions()).text()
                .equals(Keyphrase.of(before.conditions()).text());
            boolean fingerprintChanged = !after.fingerprint().equals(before.fingerprint());

            assertThat(eligibilityChanged).as("%s이 eligibility를 바꾸는가", component)
                .isEqualTo(role == Role.ELIGIBILITY);
            assertThat(conditionsChanged).as("%s이 conditions를 바꾸는가", component)
                .isEqualTo(role == Role.MUSIC_CONDITIONS);
            assertThat(keyphraseChanged).as("%s이 검색 문장을 바꾸는가", component)
                .isEqualTo(conditionsChanged);
            assertThat(fingerprintChanged).as("%s이 지문을 바꾸는가", component)
                .isEqualTo(eligibilityChanged || conditionsChanged);
        }
    }
}
