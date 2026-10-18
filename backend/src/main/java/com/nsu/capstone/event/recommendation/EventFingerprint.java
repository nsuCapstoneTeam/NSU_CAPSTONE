package com.nsu.capstone.event.recommendation;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.Genre;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * isEventChanged 지문(03 행사 API §8.3). 추천의 입력인 필드만 담는다: 시간대, 공연시간, 지역,
 * 예산, 공연 형태, 설명, 행사 종류, 장르. 장르는 순서와 관계없이 같은 집합이면 같은 값이다.
 */
public record EventFingerprint(String value) {

    public static EventFingerprint of(Event event) {
        List<String> parts = Stream.of(
            LocalDateTime.ofInstant(event.getStartAt(), Event.ZONE).toString(),
            LocalDateTime.ofInstant(event.getEndAt(), Event.ZONE).toString(),
            Integer.toString(event.getPerformanceMinutes()),
            event.getRegionCode(),
            Long.toString(event.getBudgetMin()),
            Long.toString(event.getBudgetMax()),
            event.getArtistType() == null ? "" : event.getArtistType().name(),
            event.getDescription(),
            event.getEventType().name(),
            event.getGenres().stream().sorted().map(Genre::name).reduce("", (a, b) -> a + "," + b)
        ).toList();
        return new EventFingerprint(sha256(canonical(parts)));
    }

    /** 값 사이의 구분자가 값 안에 있어도 서로 다른 조합이 같아지지 않도록 길이를 앞에 붙인다. */
    private static String canonical(List<String> parts) {
        StringBuilder canonical = new StringBuilder();
        for (String part : parts) {
            canonical.append(part.length()).append(':').append(part).append(';');
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
