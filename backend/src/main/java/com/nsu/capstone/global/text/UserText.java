package com.nsu.capstone.global.text;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.Set;

/**
 * 사용자가 입력한 문자열의 정리와 검증(05 EVENT_PARTNER 프로필 API §2, 09 아티스트 프로필 API §2).
 * 앞뒤 공백과 보이지 않는 글자를 지운 뒤 유니코드 코드포인트 수로 센다.
 */
public final class UserText {

    /** 글자로 분류되지만 화면에 아무것도 그리지 않는 한글 채움 문자와 점자 빈칸 */
    private static final Set<Integer> BLANK_LETTERS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

    /** 앞뒤에서 지우는 서식 문자: 폭 없는 공백, 단어 결합자, BOM */
    private static final Set<Integer> EDGE_INVISIBLES = Set.of(0x200B, 0x2060, 0xFEFF);

    private UserText() {}

    /** 값이 없거나 비면 VALIDATION_ERROR다. */
    public static String required(String value, int max) {
        String trimmed = optional(value, max);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    /** 값이 없거나 보이는 글자가 없으면 null이다. 글자 수는 앞뒤 공백을 지운 뒤 센다. */
    public static String optional(String value, int max) {
        if (value == null) {
            return null;
        }
        // PostgreSQL은 NUL이 든 문자열을 저장하지 못해 그대로 두면 500이 된다
        if (value.indexOf('\u0000') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        // 짝 없는 서로게이트는 UTF-8로 바꿀 때 '?'로 조용히 바뀌므로 거절한다
        if (value.codePoints().anyMatch(cp -> cp <= Character.MAX_VALUE && Character.isSurrogate((char) cp))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String trimmed = trimSpacing(value);
        // 보이는 글자(글자·숫자·문장부호·기호)가 하나도 없으면 빈 값이다. 공백·서식 문자·결합 표식만 있는 값이
        // 그렇다. 길이보다 먼저 본다(보이지 않는 글자가 길이를 넘어도 빈 값이다)
        if (trimmed.codePoints().noneMatch(UserText::visible)) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    /**
     * 앞뒤의 공백·공백 구분자(NBSP 등)와 폭 없는 공백·단어 결합자·BOM을 지운다. strip()은 NBSP와 이 세 글자를 남긴다.
     * 다른 서식 문자는 지우지 않는다. 이모지 태그 문자(깃발)와 ZWJ(가족 이모지)가 서식 문자라서, 지우면 이모지가 깨진다.
     * 결합 표식(e + U+0301)도 앞 글자의 일부라 지우지 않는다.
     */
    private static String trimSpacing(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && spacing(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && spacing(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean spacing(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.getType(codePoint) == Character.SPACE_SEPARATOR
                || EDGE_INVISIBLES.contains(codePoint);
    }

    /** 유니코드 일반 범주가 글자(L)·숫자(N)·문장부호(P)·기호(S)이면서 아무것도 그리지 않는 글자가 아니다 */
    private static boolean visible(int codePoint) {
        if (BLANK_LETTERS.contains(codePoint)) {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.UPPERCASE_LETTER,
                    Character.LOWERCASE_LETTER,
                    Character.TITLECASE_LETTER,
                    Character.MODIFIER_LETTER,
                    Character.OTHER_LETTER,
                    Character.DECIMAL_DIGIT_NUMBER,
                    Character.LETTER_NUMBER,
                    Character.OTHER_NUMBER,
                    Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION,
                    Character.MATH_SYMBOL,
                    Character.CURRENCY_SYMBOL,
                    Character.MODIFIER_SYMBOL,
                    Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }
}
