package com.nsu.capstone.event;

import static com.nsu.capstone.event.EventContentFixtures.alternate;
import static com.nsu.capstone.event.EventContentFixtures.sampleWithDetails;
import static com.nsu.capstone.event.RecordReflection.names;
import static com.nsu.capstone.event.RecordReflection.rebuildWith;
import static com.nsu.capstone.event.RecordReflection.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.presentation.dto.EventRequest;
import com.nsu.capstone.event.presentation.dto.EventResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NSU-121처럼 행사 필드를 더할 때 어느 곳을 빠뜨려도 실패하게 하는 안전망(순수 단위 테스트, ADR
 * event_management/04). 같은 필드 목록이 EventRequest, EventContent(해시), Event.apply, EventResponse에
 * 따로 있다. 저장 형식·API 이름·추천 입력이 서로 달라 목록을 하나로 만들 수 없으므로 "목록끼리 어긋나면
 * 실패한다"로 대신한다. 필드를 더하는 사람이 할 일:
 * (1) EventContent에 컴포넌트를 더한다. new EventContent(...)가 컴파일되지 않는 곳을 모두 고친다.
 * (2) EventContentFixtures.sampleWithDetails()와 alternate()에 null이 아닌, 서로 다른 값을 정한다.
 * (3) 지문·추천 입력은 FingerprintSnapshotAgreementTest의 표에 한 줄을 더한다.
 * 그다음 실패하는 테스트가 가리키는 곳(Event.apply, 해시, 응답, 요청)을 고친다.
 */
@DisplayName("행사 필드 목록 일치")
class EventFieldCoverageTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    static Event event(EventContent content) {
        return Event.register(UUID.randomUUID(), UUID.randomUUID(), content, CLOCK);
    }

    @Test
    @DisplayName("본문 해시는 모든 컴포넌트(선택정보 10개 포함)에 따라 달라진다")
    void requestHashCoversEveryComponent() throws Exception {
        EventContent base = sampleWithDetails();
        for (String component : names(EventContent.class)) {
            if (component.equals("details")) {
                continue;
            }
            EventContent changed = rebuildWith(EventContent.class, base, component, value(alternate(), component));
            assertThat(changed.requestHash()).as("%s이 해시에 들어 있다", component).isNotEqualTo(base.requestHash());
        }
        for (String field : names(EventDetails.class)) {
            EventDetails mixed = rebuildWith(EventDetails.class, base.details(), field, value(alternate().details(), field));
            EventContent changed = rebuildWith(EventContent.class, base, "details", mixed);
            assertThat(changed.requestHash()).as("details.%s가 해시에 들어 있다", field).isNotEqualTo(base.requestHash());
        }
    }

    @Test
    @DisplayName("Event는 EventContent의 모든 컴포넌트를 그대로 저장한다")
    void entityStoresEveryComponent() throws Exception {
        EventContent content = sampleWithDetails();
        Event event = event(content);
        for (String component : names(EventContent.class)) {
            Object actual = switch (component) {
                case "start" -> LocalDateTime.ofInstant(event.getStartAt(), Event.ZONE);
                case "end" -> LocalDateTime.ofInstant(event.getEndAt(), Event.ZONE);
                default -> Event.class.getMethod("get" + Character.toUpperCase(component.charAt(0))
                    + component.substring(1)).invoke(event);
            };
            assertThat(actual).as("Event.%s", component).isEqualTo(value(content, component));
        }
    }

    @Test
    @DisplayName("요청과 응답 DTO가 EventContent의 모든 컴포넌트를 가진다(시각은 날짜와 시각으로 나뉜다)")
    void requestAndResponseExposeEveryComponent() {
        for (String component : names(EventContent.class)) {
            List<String> wireNames = switch (component) {
                case "start" -> List.of("startDate", "startTime");
                case "end" -> List.of("endDate", "endTime");
                default -> List.of(component);
            };
            assertThat(names(EventRequest.class)).as("EventRequest.%s", component).containsAll(wireNames);
            assertThat(names(EventResponse.class)).as("EventResponse.%s", component).containsAll(wireNames);
        }
    }
}
