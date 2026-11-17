package com.nsu.capstone.verification.externalaccount.domain;

/**
 * 플랫폼이 준 문자열은 믿지 않는 입력이다(06 §6.4의 9). 제어 문자를 지우고 앞뒤 공백을 지운 뒤 길이를 자른다.
 * HTML을 막는 일은 하지 않는다. 화면이 이 값을 HTML이 아닌 글자로만 그린다.
 */
public final class DisplayText {

    /** 계정 이름의 최대 길이(코드 포인트). */
    public static final int DISPLAY_NAME_MAX = 100;

    /** 영상 제목의 최대 길이(코드 포인트). */
    public static final int TITLE_MAX = 200;

    private DisplayText() {}

    /** 정리한 뒤 비어 있으면 {@code null}이다. */
    public static String clean(String raw, int maxCodePoints) {
        if (raw == null) {
            return null;
        }
        StringBuilder withoutControls = new StringBuilder(raw.length());
        raw.codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.CONTROL)
                .forEach(withoutControls::appendCodePoint);
        String stripped = withoutControls.toString().strip();
        if (stripped.codePointCount(0, stripped.length()) > maxCodePoints) {
            stripped = stripped.substring(0, stripped.offsetByCodePoints(0, maxCodePoints));
        }
        return stripped.isEmpty() ? null : stripped;
    }
}
