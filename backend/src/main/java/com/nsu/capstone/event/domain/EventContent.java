package com.nsu.capstone.event.domain;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 등록·수정 본문을 검사해 얻은 행사 내용. 시간대는 Asia/Seoul 벽시계 값이다.
 * genres는 03 §3.3 표의 순서로 정렬되어 있다.
 */
public record EventContent(
    String name,
    EventType eventType,
    LocalDateTime start,
    LocalDateTime end,
    int performanceMinutes,
    String venue,
    String regionCode,
    ArtistType artistType,
    List<Genre> genres,
    long budgetMin,
    long budgetMax,
    int expectedAudience,
    String description,
    Visibility visibility,
    EventDetails details
) {

    private static final Duration MAX_WINDOW = Duration.ofHours(24);
    private static final long MAX_BUDGET = 1_000_000_000L;
    private static final int MAX_AUDIENCE = 1_000_000;
    private static final int MAX_GENRES = 3;
    /** 형식만 본다. 목록에 있는지는 DB가 필요하므로 서비스가 본다. */
    private static final Pattern REGION_CODE = Pattern.compile("\\d{5}");

    public EventContent {
        name = requiredText(name, 100);
        nonNull(eventType);
        nonNull(start);
        nonNull(end);
        Duration window = Duration.between(start, end);
        check(window.isPositive() && window.compareTo(MAX_WINDOW) <= 0);
        // 공연시간의 상한이 시간대 길이라서 시간대 검사가 먼저다.
        check(performanceMinutes >= 1 && performanceMinutes <= window.toMinutes());
        venue = requiredText(venue, 200);
        check(REGION_CODE.matcher(nonNull(regionCode)).matches());
        genres = inTableOrder(genres);
        check(0 <= budgetMin && budgetMin <= budgetMax && budgetMax <= MAX_BUDGET);
        check(1 <= expectedAudience && expectedAudience <= MAX_AUDIENCE);
        description = requiredText(description, 2000);
        nonNull(visibility);
        details = details == null ? EventDetails.empty() : normalized(details);
    }

    /**
     * 등록 본문 해시(ADR event_management/03). 24개 값을 아래 순서로 쓴다. 순서나 표기를 바꾸면
     * events.request_hash에 저장된 값과 달라져, 이미 쓴 멱등 키의 재전송이 모두 422가 된다.
     */
    public String requestHash() {
        return CanonicalDigest.start()
            .text(name)
            .code(eventType)
            .time(start)
            .time(end)
            .number(performanceMinutes)
            .text(venue)
            .text(regionCode)
            .code(artistType)
            .codeSet(genres)
            .number(budgetMin)
            .number(budgetMax)
            .number(expectedAudience)
            .text(description)
            .code(visibility)
            .text(details.rehearsal())
            .text(details.stageSize())
            .flag(details.paProvided())
            .text(details.microphones())
            .text(details.instruments())
            .flag(details.waitingRoomProvided())
            .flag(details.mealProvided())
            .flag(details.transportationFeeProvided())
            .flag(details.accommodationProvided())
            .text(details.otherConditions())
            .hex();
    }

    /**
     * 시작 ≤ 지금이면 400 EVENT_START_IN_PAST(03 §3.2). 불변식이 아니라서 생성자가 아니라 서비스가 제자리에서
     * 부른다. 등록은 키를 INSERT·flush로 선점한 뒤 같은 쓰기 트랜잭션 안에서(ADR 03), 수정은 저장된 행사의
     * 취소·시작 409 뒤에(03 §5.2, §9) 부른다. 시각은 Clock의 zone으로 읽는다.
     */
    public void requireStartAfter(Clock clock) {
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.EVENT_START_IN_PAST);
        }
    }

    // 검사 도우미. 모두 VALIDATION_ERROR다(ADR-007: 어느 필드가 틀렸는지 알리지 않는다).

    private static void check(boolean valid) {
        if (!valid) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static <T> T nonNull(T value) {
        check(value != null);
        return value;
    }

    private static boolean hasNoNul(String text) {
        return text.indexOf('\0') < 0;
    }

    /** 앞뒤 공백을 지운 뒤 1자 이상 maxLength자 이하(UTF-16 code unit)이고 NUL이 없어야 한다. */
    private static String requiredText(String value, int maxLength) {
        String text = nonNull(value).strip();
        check(!text.isEmpty() && text.length() <= maxLength && hasNoNul(text));
        return text;
    }

    /** 앞뒤 공백을 지우고 빈 값은 null로 바꾼다. 최대 길이와 NUL은 requiredText와 같다. */
    private static String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        check(text.length() <= maxLength && hasNoNul(text));
        return text.isEmpty() ? null : text;
    }

    /**
     * EventDetails의 정규화는 EventDetails 생성자가 아니라 여기에 둔다. EventDetails는 JPA embeddable이라
     * Hibernate가 행을 읽을 때도 생성자를 부르므로, 거기서 검사하면 규칙에 맞지 않는 옛 행 때문에 내용이
     * 필요 없는 잠금 조회·취소·삭제까지 실패할 수 있다.
     */
    private static EventDetails normalized(EventDetails details) {
        return new EventDetails(
            optionalText(details.rehearsal(), 100),
            optionalText(details.stageSize(), 100),
            details.paProvided(),
            optionalText(details.microphones(), 100),
            optionalText(details.instruments(), 100),
            details.waitingRoomProvided(),
            details.mealProvided(),
            details.transportationFeeProvided(),
            details.accommodationProvided(),
            optionalText(details.otherConditions(), 500)
        );
    }

    private static List<Genre> inTableOrder(List<Genre> genres) {
        nonNull(genres);
        check(!genres.isEmpty() && genres.size() <= MAX_GENRES);
        EnumSet<Genre> distinct = EnumSet.noneOf(Genre.class);
        for (Genre genre : genres) {
            check(genre != null && distinct.add(genre));
        }
        return List.copyOf(distinct);
    }
}
