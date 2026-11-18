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
     * 제어 문자, 줄·문단 구분자, 보이지 않는 서식 문자(Cf 전반), 글자 모양이 빈 문자를 지운다. 방향을 바꾸거나 이름 앞뒤에
     * 끼워 다른 이름과 같은 모양을 만들거나 이름이 비어 보이게 하는 문자들이다. ZWJ(U+200D)·ZWNJ(U+200C)는 이모지 조합과
     * 한글 등 정상 글자에 쓰이므로 남긴다.
     */
    private static boolean isUnsafeForDisplay(int codePoint) {
        if (codePoint == 0x200C || codePoint == 0x200D) {
            return false;
        }
        int type = Character.getType(codePoint);
        if (type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR) {
            return true;
        }
        // 글자 모양이 비어 보이는 문자: NBSP(strip()이 못 지움), 한글 채움 문자, 점자 빈칸
        return codePoint == 0x00A0
                || codePoint == 0x3164
                || codePoint == 0xFFA0
                || codePoint == 0x115F
                || codePoint == 0x1160
                || codePoint == 0x2800;
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
