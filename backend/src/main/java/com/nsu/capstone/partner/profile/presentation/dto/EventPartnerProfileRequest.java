package com.nsu.capstone.partner.profile.presentation.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.partner.profile.application.ProfileContent;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 수정 본문(05 EVENT_PARTNER 프로필 API §2). enum과 코드는 문자열로 받아 직접 검사한다.
 * JSON 변환 단계에서 실패해 Spring 표준 400이 나가지 않게 하기 위해서다(ADR-007 §4.4).
 * 그래서 API 문서의 필수 여부와 허용 값은 @Schema로 보정한다(00 공통 규칙 §10.1).
 */
public record EventPartnerProfileRequest(
    @Schema(requiredMode = REQUIRED) String name,
    String organization,
    String bio,
    @ArraySchema(
        arraySchema = @Schema(requiredMode = REQUIRED),
        schema = @Schema(implementation = EventType.class)
    ) List<String> eventTypes,
    @ArraySchema(arraySchema = @Schema(requiredMode = REQUIRED)) List<String> regionCodes
) {

    private static final int NAME_MAX = 40;
    private static final int ORGANIZATION_MAX = 60;
    private static final int BIO_MAX = 700;
    private static final int MAX_LIST = 3;
    /** 글자로 분류되지만 화면에 아무것도 그리지 않는 한글 채움 문자와 점자 빈칸 */
    private static final Set<Integer> BLANK_LETTERS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

    /** 글자 수는 앞뒤 공백을 지운 뒤 센다. 선택 문자열은 비면 null이다(05 §2). */
    public ProfileContent toContent() {
        return new ProfileContent(
            required(name, NAME_MAX),
            optional(organization, ORGANIZATION_MAX),
            optional(bio, BIO_MAX),
            eventTypes(eventTypes),
            list(regionCodes)
        );
    }

    /** 허용 목록(EventType) 밖의 값, 4개 이상, 중복은 거절하고 enum 선언 순서로 돌려준다(05 §2, 03 §3.3). */
    private static List<String> eventTypes(List<String> values) {
        EnumSet<EventType> types = EnumSet.noneOf(EventType.class);
        for (String value : list(values)) {
            EventType type = eventType(value);
            if (!types.add(type)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
        return types.stream().map(EventType::name).toList();
    }

    private static EventType eventType(String value) {
        try {
            return EventType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static List<String> list(List<String> values) {
        if (values == null || values.size() > MAX_LIST) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return values;
    }

    private static String required(String value, int max) {
        String trimmed = optional(value, max);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    private static String optional(String value, int max) {
        if (value == null) {
            return null;
        }
        // PostgreSQL은 NUL이 든 문자열을 저장하지 못해 그대로 두면 500이 된다
        if (value.indexOf('\u0000') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String trimmed = value.strip();
        // 보이는 글자(글자·숫자·문장부호·기호)가 하나도 없으면 빈 값이다. 공백·서식 문자·결합 표식만 있는 값이
        // 그렇다. 길이보다 먼저 본다(보이지 않는 글자가 길이를 넘어도 빈 값이다)
        if (trimmed.codePoints().noneMatch(EventPartnerProfileRequest::visible)) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    /** 유니코드 일반 범주가 글자(L)·숫자(N)·문장부호(P)·기호(S)이면서 아무것도 그리지 않는 글자가 아니다 */
    private static boolean visible(int codePoint) {
        if (BLANK_LETTERS.contains(codePoint)) {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                 Character.MODIFIER_LETTER, Character.OTHER_LETTER,
                 Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
                 Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
                 Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION,
                 Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL,
                 Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }
}
