package com.nsu.capstone.event.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/**
 * 길이 접두 정규 문자열과 SHA-256의 유일한 구현(ADR event_management/04).
 */
final class CanonicalDigest {

    private final StringBuilder canonical = new StringBuilder();
    private final Set<String> tags = new HashSet<>();

    private CanonicalDigest() {}

    static CanonicalDigest start() {
        return new CanonicalDigest();
    }

    /**
     * null은 {@code -1;}이다. 빈 문자열({@code 0:;})과 다르다. 위치 구역의 값이라 이름표 구역에 들어선
     * 뒤에는 쓸 수 없다. {@code code}·{@code number}·{@code flag}·{@code time}·{@code codeSet}도 이 메서드를 거친다.
     */
    CanonicalDigest text(String value) {
        if (!tags.isEmpty()) {
            throw new IllegalStateException("이름표 구역에 들어선 뒤에는 위치 값을 쓸 수 없다");
        }
        if (value == null) {
            canonical.append("-1;");
        } else {
            appendToken(value);
        }
        return this;
    }

    /**
     * 이름표 구역(ADR event_management/05 결정 4). 위치 구역 뒤에 {@code 길이:이름표;길이:값;}을 쓰고,
     * 값이 null이면 아무것도 쓰지 않는다. 한 정규 문자열에서 같은 이름표를 두 번 쓰면 값이 null이어도
     * {@link IllegalStateException}이다. 같은 타입의 두 필드가 이름표를 나눠 쓰면 값을 맞바꾼 본문이 같은
     * 문자열이 되기 때문이다. 이름표 문자열은 저장 형식의 일부라 한 번 배포하면 바꾸지 않는다.
     */
    CanonicalDigest tagged(String tag, Enum<?> value) {
        if (!tags.add(tag)) {
            throw new IllegalStateException("같은 이름표를 두 번 쓸 수 없다: " + tag);
        }
        if (value != null) {
            appendToken(tag);
            appendToken(value.name());
        }
        return this;
    }

    private void appendToken(String value) {
        canonical.append(value.length()).append(':').append(value).append(';');
    }

    /** enum은 {@code name()}이다. 상수 이름을 바꾸면 해시가 달라진다. */
    CanonicalDigest code(Enum<?> value) {
        return text(value == null ? null : value.name());
    }

    CanonicalDigest number(long value) {
        return text(Long.toString(value));
    }

    /** {@code "true"}, {@code "false"}, null이다. */
    CanonicalDigest flag(Boolean value) {
        return text(value == null ? null : value.toString());
    }

    /** {@link LocalDateTime#toString()}이라 초가 0이면 초를 생략한다({@code 2026-11-06T18:00}). */
    CanonicalDigest time(LocalDateTime value) {
        return text(value == null ? null : value.toString());
    }

    /**
     * enum 집합(장르가 이 형식이다). 선언 순서로 정렬해 이름마다 앞에 쉼표를 붙여 잇는다. 입력 순서와
     * 관계없이 같은 집합이면 같은 값이다(03 §8.3).
     */
    CanonicalDigest codeSet(Collection<? extends Enum<?>> value) {
        return text(
                value == null ? null : value.stream().sorted().map(Enum::name).reduce("", (a, b) -> a + "," + b));
    }

    /** 정규 문자열 UTF-8 바이트의 SHA-256을 소문자 hex 64자로 돌려준다. */
    String hex() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없다", exception);
        }
    }

    String canonical() {
        return canonical.toString();
    }
}
