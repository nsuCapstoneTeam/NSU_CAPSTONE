package com.nsu.capstone.event.application;

import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.Genre;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * 등록 본문 해시(ADR event_management/03). 검증과 정규화를 마친 EventContent의 모든 필드로 만들어,
 * 같은 멱등 키로 다시 온 요청이 처음 본문과 같은지 비교한다. 추천 입력 필드만 담는
 * EventFingerprint와 다르다. 방식은 같다: 길이를 앞에 붙인 정규 문자열의 SHA-256.
 */
final class EventContentHash {

    private EventContentHash() {
    }

    static String of(EventContent content) {
        EventDetails details = content.details();
        List<String> parts = Stream.of(
            content.name(),
            content.eventType().name(),
            content.start().toString(),
            content.end().toString(),
            Integer.toString(content.performanceMinutes()),
            content.venue(),
            content.regionCode(),
            content.artistType() == null ? null : content.artistType().name(),
            content.genres().stream().sorted().map(Genre::name).reduce("", (a, b) -> a + "," + b),
            Long.toString(content.budgetMin()),
            Long.toString(content.budgetMax()),
            Integer.toString(content.expectedAudience()),
            content.description(),
            content.visibility().name(),
            details.rehearsal(),
            details.stageSize(),
            string(details.paProvided()),
            details.microphones(),
            details.instruments(),
            string(details.waitingRoomProvided()),
            string(details.mealProvided()),
            string(details.transportationFeeProvided()),
            string(details.accommodationProvided()),
            details.otherConditions()
        ).toList();
        return sha256(canonical(parts));
    }

    private static String string(Boolean value) {
        return value == null ? null : value.toString();
    }

    /** 값 안에 구분자가 있어도 서로 다른 조합이 같아지지 않도록 길이를 앞에 붙인다. null은 -1이다. */
    private static String canonical(List<String> parts) {
        StringBuilder canonical = new StringBuilder();
        for (String part : parts) {
            if (part == null) {
                canonical.append("-1;");
            } else {
                canonical.append(part.length()).append(':').append(part).append(';');
            }
        }
        return canonical.toString();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없다", exception);
        }
    }
}
