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

    /**
     * 제어 문자와 줄·문단 구분자, 그리고 글자 모양을 속이는 서식 문자를 지운다. 방향을 바꾸거나(U+202A~202E, U+2066~2069,
     * U+200E~200F) 보이지 않게 이름을 흉내 내는(U+200B, U+FEFF) 문자다. ZWJ(U+200D)·ZWNJ(U+200C)는 이모지 조합과 한글 등
     * 정상 글자에 쓰이므로 남긴다.
     */
    private static boolean isUnsafeForDisplay(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.CONTROL || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
            return true;
        }
        return codePoint == 0x200B
                || codePoint == 0xFEFF
                || (codePoint >= 0x200E && codePoint <= 0x200F)
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    /** 정리한 뒤 비어 있으면 {@code null}이다. */
    public static String clean(String raw, int maxCodePoints) {
        if (raw == null) {
            return null;
        }
        StringBuilder withoutControls = new StringBuilder(raw.length());
        raw.codePoints().filter(codePoint -> !isUnsafeForDisplay(codePoint)).forEach(withoutControls::appendCodePoint);
        String stripped = withoutControls.toString().strip();
        if (stripped.codePointCount(0, stripped.length()) > maxCodePoints) {
            stripped = stripped.substring(0, stripped.offsetByCodePoints(0, maxCodePoints));
        }
        return stripped.isEmpty() ? null : stripped;
    }
}
