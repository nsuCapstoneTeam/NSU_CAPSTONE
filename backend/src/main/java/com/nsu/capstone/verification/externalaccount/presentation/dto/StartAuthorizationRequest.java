package com.nsu.capstone.verification.externalaccount.presentation.dto;

import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 연결 시작 요청(06 §3). {@code replaceExternalAccountId} 키가 빠지면 {@code null}이다.
 *
 * @param replaceExternalAccountId 교체를 승인한 지금 연결의 ID. 문자열로 받아 형식 오류를 {@code VALIDATION_ERROR}로 낸다
 */
public record StartAuthorizationRequest(String provider, String replaceExternalAccountId) {

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    /** 검증한 교체 승인 ID. 없으면 {@code null}이다. YouTube만 받는다. */
    public UUID validatedReplaceId() {
        if (!"YOUTUBE".equals(provider)) {
            throw validationError("provider는 YOUTUBE여야 한다");
        }
        return parseOptionalUuid(replaceExternalAccountId);
    }

    /** 키가 빠졌으면 {@code null}, 값이 있으면 정규형 UUID여야 한다. 빈 문자열은 거절한다. */
    static UUID parseOptionalUuid(String value) {
        if (value == null) {
            return null;
        }
        if (!CANONICAL_UUID.matcher(value).matches()) {
            throw validationError("replaceExternalAccountId 형식이 올바르지 않다");
        }
        return UUID.fromString(value);
    }

    static ExternalAccountException validationError(String message) {
        return new ExternalAccountException(ExternalAccountFailure.VALIDATION_ERROR, message);
    }
}
