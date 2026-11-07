package com.nsu.capstone.event.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;

/**
 * 길이 접두 정규 문자열과 SHA-256의 유일한 구현(ADR event_management/04).
 */
final class CanonicalDigest {

    private final StringBuilder canonical = new StringBuilder();

    private CanonicalDigest() {
    }

    static CanonicalDigest start() {
        return new CanonicalDigest();
    }

    /** null은 {@code -1;}이다. 빈 문자열({@code 0:;})과 다르다. */
    CanonicalDigest text(String value) {
        if (value == null) {
            canonical.append("-1;");
        } else {
            canonical.append(value.length()).append(':').append(value).append(';');
        }
        return this;
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
        return text(value == null ? null
            : value.stream().sorted().map(Enum::name).reduce("", (a, b) -> a + "," + b));
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
