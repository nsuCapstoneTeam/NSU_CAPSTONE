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
 * 검출하지 못한다. 그래서 추천 입력은 eligibility와 aiSource만, 지문은 fingerprint 값만 따로 비교한다.
 *
 * <p>필드를 더하는 사람은 ROLE 표에 한 줄을 더한다. 표의 키 집합은 EventContent의 컴포넌트 집합과 같아야 해서,
 * 빠뜨리면 첫 테스트가 실패한다.
 *
 * <p>한계: 여기서 "추천 입력"은 지금의 EligibilityInput과 AiEventSource다. 서버 협의 016에 따라 AI 입력이
 * keyphrase로 바뀌면 비교 대상도 실제 AI 요청을 만드는 입력으로 옮겨야 한다(NSU-11·NSU-63의 전제).
 */
@DisplayName("지문과 추천 입력의 필드 일치")
class FingerprintSnapshotAgreementTest {

    enum Role {
        /** EligibilityInput에 실린다. */
        ELIGIBILITY,
        /** AiEventSource에 실린다. */
        AI_SOURCE,
        /** 추천 입력이 아니다. 지문도 바뀌지 않는다. */
        NONE
    }

    /** 03 §8.2(snapshot)·§8.3(지문) 표를 컴포넌트 이름으로 옮긴 것. 추천 입력이면 지문도 바뀐다. */
    static final Map<String, Role> ROLE = Map.ofEntries(
        entry("name", Role.NONE),
        entry("eventType", Role.AI_SOURCE),
        entry("start", Role.ELIGIBILITY),
        entry("end", Role.ELIGIBILITY),
        entry("performanceMinutes", Role.ELIGIBILITY),
        entry("venue", Role.NONE),
        entry("regionCode", Role.ELIGIBILITY),
        entry("artistType", Role.ELIGIBILITY),
        entry("genres", Role.AI_SOURCE),
        entry("budgetMin", Role.ELIGIBILITY),
        entry("budgetMax", Role.ELIGIBILITY),
        entry("expectedAudience", Role.NONE),
        entry("description", Role.AI_SOURCE),
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
    @DisplayName("컴포넌트를 바꾸면 표가 정한 쪽만 바뀌고, 지문은 추천 입력이 바뀔 때만 바뀐다")
    void eligibilityAiSourceAndFingerprintChangeAsTheTableSays() throws Exception {
        EventContent base = sampleWithDetails();
        EventSnapshot before = snapshot(base);

        for (String component : names(EventContent.class)) {
            EventSnapshot after = snapshot(
                rebuildWith(EventContent.class, base, component, value(alternate(), component)));
            Role role = ROLE.get(component);

            boolean eligibilityChanged = !after.eligibility().equals(before.eligibility());
            boolean aiSourceChanged = !after.aiSource().equals(before.aiSource());
            boolean fingerprintChanged = !after.fingerprint().equals(before.fingerprint());

            assertThat(eligibilityChanged).as("%s이 eligibility를 바꾸는가", component)
                .isEqualTo(role == Role.ELIGIBILITY);
            assertThat(aiSourceChanged).as("%s이 aiSource를 바꾸는가", component)
                .isEqualTo(role == Role.AI_SOURCE);
            assertThat(fingerprintChanged).as("%s이 지문을 바꾸는가", component)
                .isEqualTo(eligibilityChanged || aiSourceChanged);
        }
    }
}
